package com.taobao.pamirs.schedule.strategy;

import com.taobao.pamirs.schedule.ConsoleManager;
import com.taobao.pamirs.schedule.IScheduleTaskDeal;
import com.taobao.pamirs.schedule.ScheduleUtil;
import com.taobao.pamirs.schedule.taskmanager.IScheduleDataManager;
import com.taobao.pamirs.schedule.taskmanager.TBScheduleManagerStatic;
import com.taobao.pamirs.schedule.zk.ScheduleDataManager4ZK;
import com.taobao.pamirs.schedule.zk.ScheduleStrategyDataManager4ZK;
import com.taobao.pamirs.schedule.zk.ZKManager;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 调度服务器构造器
 *
 * @author xuannan
 *
 */
public class TBScheduleManagerFactory implements ApplicationContextAware {

    protected static transient Logger logger = LoggerFactory.getLogger(TBScheduleManagerFactory.class);

    private Map<String, String> zkConfig;

    protected ZKManager zkManager;


    /**
     * 是否启动调度管理，如果只是做系统管理，应该设置为false
     */
    public boolean start = true;
    private int timerInterval = 2000;
    /**
     * ManagerFactoryTimerTask上次执行的时间戳。<br/>
     * zk环境不稳定，可能导致所有task自循环丢失，调度停止。<br/>
     * 外层应用，通过jmx暴露心跳时间，监控这个tbschedule最重要的大循环。<br/>
     */
    public volatile long timerTaskHeartBeatTS = System.currentTimeMillis();

    /**
     * 调度配置中心客户端
     */
    private IScheduleDataManager scheduleTaskManager;
    private ScheduleStrategyDataManager4ZK scheduleStrategyManager;

    private Map<String, List<IStrategyTask>> managerMap = new ConcurrentHashMap<String, List<IStrategyTask>>();

    private ApplicationContext applicationcontext;
    private String uuid;
    private String ip;
    private String hostName;

    private Timer timer;
    private ManagerFactoryTimerTask timerTask;
    protected Lock lock = new ReentrantLock();

    volatile String errorMessage = "No config Zookeeper connect infomation";
    private InitialThread initialThread;

    public TBScheduleManagerFactory() {
        this.ip = ScheduleUtil.getLocalIP();
        this.hostName = ScheduleUtil.getLocalHostName();
    }

    public void init() throws Exception {
        Properties properties = new Properties();
        for (Map.Entry<String, String> e : this.zkConfig.entrySet()) {
            properties.put(e.getKey(), e.getValue());
        }
        this.init(properties);
    }

    public void reInit(Properties p) throws Exception {
        if (this.start == true || this.timer != null || this.managerMap.size() > 0) {
            throw new Exception("调度器有任务处理，不能重新初始化");
        }
        this.init(p);
    }

    public void init(Properties p) throws Exception {
        if (this.initialThread != null) {
            this.initialThread.stopThread();
        }
        this.lock.lock();
        try {
            this.scheduleTaskManager = null;
            this.scheduleStrategyManager = null;
            ConsoleManager.setScheduleManagerFactory(this);
            if (this.zkManager != null) {
                this.zkManager.close();
            }
            this.zkManager = new ZKManager(p);
            this.errorMessage = "Zookeeper connecting ......" + this.zkManager.getConnectStr();
            initialThread = new InitialThread(this);
            initialThread.setName("TBScheduleManagerFactory-InitialThread");
            initialThread.start();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * 在Zk状态正常后回调数据初始化
     * @throws Exception
     */
    public void initialData() throws Exception {
        this.zkManager.initial(); //保证$rootpath存在，没有则创建
        this.scheduleTaskManager = new ScheduleDataManager4ZK(this.zkManager); //保证$rootpath/baseTaskType存在，没有则创建 ，并记录zk服务器上的时间zkBaseTime
        this.scheduleStrategyManager = new ScheduleStrategyDataManager4ZK(this.zkManager);  //保证$rootpath/strategy, $rootpath/factory 存在，没有则创建
        if (this.start == true) {
            // 注册调度管理器
            this.scheduleStrategyManager.registerManagerFactory(this);
            if (timer == null) {
                timer = new Timer("TBScheduleManagerFactory-Timer");
            }
            if (timerTask == null) {
                timerTask = new ManagerFactoryTimerTask(this);
                timer.schedule(timerTask, 2000, this.timerInterval);
            }
        }
    }

    /**
     * 创建调度服务器
     *
     * @param strategy
     * @return
     * @throws Exception
     */
    public IStrategyTask createStrategyTask(ScheduleStrategy strategy)   throws Exception {
        IStrategyTask result = null;
        try {
            if (Kind.Schedule == strategy.getKind()) {
                String baseTaskType = ScheduleUtil.splitBaseTaskTypeFromTaskType(strategy.getTaskName());
                String ownSign = ScheduleUtil.splitOwnsignFromTaskType(strategy.getTaskName());
                result = new TBScheduleManagerStatic(this, baseTaskType, ownSign, scheduleTaskManager);
            } else if (Kind.Java == strategy.getKind()) {
                result = (IStrategyTask) Class.forName(strategy.getTaskName()).newInstance();
                result.initialTaskParameter(strategy.getStrategyName(), strategy.getTaskParameter());
            } else if (Kind.Bean == strategy.getKind()) {
                result = (IStrategyTask) this.getBean(strategy.getTaskName());
                result.initialTaskParameter(strategy.getStrategyName(), strategy.getTaskParameter());
            }
        } catch (Exception e) {
            logger.error("strategy 获取对应的java or bean 出错,schedule并没有加载该任务,请确认" + strategy.getStrategyName(), e);
        }
        return result;
    }

    /**
     * 每2秒检测1次机器是否存活(即是否与zk保持连接)，存活的话则调用 reRegisterManagerFactory()方法
     *
     * @throws Exception
     */
    public void refresh() throws Exception {
        this.lock.lock();
        try {
            // 判断状态是否终止
            ManagerFactoryInfo managerFactoryInfo = null;
            boolean isException = false;
            try {
                //managerFactoryInfo示例：{"uuid":"10.187.133.52$host-10-187-133-52$786D516F7FA84FB6BF501B9B4FFF073D$0000000477","start":true}
                managerFactoryInfo = this.getScheduleStrategyManager().loadManagerFactoryInfo(this.getUuid());
            } catch (Exception e) {
                isException = true;
                logger.error("获取服务器信息有误：uuid=" + this.getUuid(), e);
            }
            if (isException == true) {
                try {
                    stopServer(null); // 停止所有的调度任务
                    this.getScheduleStrategyManager().unRregisterManagerFactory(this);
                } finally {
                    reRegisterManagerFactory();
                }
            } else if (managerFactoryInfo.isStart() == false) {
                stopServer(null); // 停止所有的调度任务
                this.getScheduleStrategyManager().unRregisterManagerFactory(this);
            } else {
                reRegisterManagerFactory();
            }
        } finally {
            this.lock.unlock();
        }
    }


    public void reRegisterManagerFactory() throws Exception {
        //重新分配调度器
        List<String> stopStrategyNameList = this.getScheduleStrategyManager().registerManagerFactory(this);
        for (String strategyName : stopStrategyNameList) {
            this.stopServer(strategyName);
        }
        this.assignScheduleServer();
        this.reRunScheduleServer();
    }

    /**
     * 根据策略重新分配调度任务的机器
     *
     * @throws Exception
     */
    public void assignScheduleServer() throws Exception {
        //获得这个uuid所在机器能够参与的所有任务列表
        List<ScheduleStrategyRunntime> cheduleStrategyRunntimeList=this.scheduleStrategyManager.loadAllScheduleStrategyRunntimeByUUID(this.uuid);

        for (ScheduleStrategyRunntime scheduleStrategyRunntime :cheduleStrategyRunntimeList ) {
            //获得指定strategyName下的所有机器列表的data数据，并且是排过序的，排序规则：按照uuid中的自增序列号进行升序排列
            List<ScheduleStrategyRunntime> factoryList = this.scheduleStrategyManager.loadAllScheduleStrategyRunntimeByTaskType(scheduleStrategyRunntime.getStrategyName());
            if (factoryList.size() == 0 ) { //如果1台机器都没有就放弃当前任务了
                continue;
            }
            //Leader的算法：uuid中自境序列号中最小的那个
            boolean isLeader = this.isLeader(this.uuid, factoryList);
            if(!isLeader){ //进行任务分配的只有Leader哦，非Leader的直接 continue
                continue;
            }

            //获得指定任务的概要配置信息
            ScheduleStrategy scheduleStrategy = this.scheduleStrategyManager.loadStrategy(scheduleStrategyRunntime.getStrategyName());

            //参数含义：实际机器数量，分配的线程组数量(1项任务分配给多少个团队来执行)，分配的单JVM最大线程组数量(1项任务在1个房子<机器>里最多允许多少个团队来执行)
            //lzc 20171224:上面第3个参数（分配的单JVM最大线程组数量(1项任务在1个房子<机器>里最多允许多少个团队来执行)）,已作废
            //举例说明， 8，8，0 => taskItemNums=[1,1,1,1,1,1,1,1 ]
            //举例说明， 4，8，0 => taskItemNums=[2,2,2,2 ]
            //举例说明， 3，8，0 => taskItemNums=[3,3,2 ]
            int[] taskItemNums = ScheduleUtil.assignTaskNumber(factoryList.size(), scheduleStrategy.getAssignNum(), scheduleStrategy.getNumOfSingleServer());
            for (int i = 0; i < factoryList.size(); i++) {
                ScheduleStrategyRunntime factory = factoryList.get(i);
                //更新请求的服务器数量
                this.scheduleStrategyManager.updateStrategyRunntimeReqestNum(scheduleStrategyRunntime.getStrategyName(),factory.getUuid(), taskItemNums[i]);
            }
        }
    }

    /**
     * Leader的算法：uuid中自境序列号中最小的那个
     *
     * //TODO:一点疑问：factoryList已经是有序的，为什么不用当前uuid.equals(factoryList.get(0))来判断呢
     * @param uuid
     * @param factoryList
     * @return
     */
    public boolean isLeader(String uuid, List<ScheduleStrategyRunntime> factoryList) {
        try {
            long no = Long.parseLong(uuid.substring(uuid.lastIndexOf("$") + 1));
            for (ScheduleStrategyRunntime server : factoryList) {
                if (no > Long.parseLong(server.getUuid().substring( server.getUuid().lastIndexOf("$") + 1)) ) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("判断Leader出错：uui=" + uuid, e);
            //TODO:既然出错了，为什么直接返回true，这里会不会有问题
            return true;
        }
    }

    public void reRunScheduleServer() throws Exception {
        //获得这个uuid所在机器能够参与的所有任务列表
        List<ScheduleStrategyRunntime> cheduleStrategyRunntimeList=this.scheduleStrategyManager.loadAllScheduleStrategyRunntimeByUUID(this.uuid);

        for (ScheduleStrategyRunntime run : cheduleStrategyRunntimeList ) {
            List<IStrategyTask> list = this.managerMap.get(run.getStrategyName());
            if (list == null) {
                list = new ArrayList<IStrategyTask>();
                this.managerMap.put(run.getStrategyName(), list);
            }
            while (list.size() > run.getRequestNum() && list.size() > 0) {
                IStrategyTask task = list.remove(list.size() - 1);
                try {
                    task.stop(run.getStrategyName());
                } catch (Throwable e) {
                    logger.error("注销任务错误：strategyName=" + run.getStrategyName(), e);
                }
            }
            //不足，增加调度器
            ScheduleStrategy strategy = this.scheduleStrategyManager.loadStrategy(run.getStrategyName());

            // requestNumr=0(Leader尚未给其分配任务项 或 Leader 分配时发现不需要这台机器来处理)时,下面where直接结束,等待下一个2秒 再次factory.refresh()
            // requestNumr=1(Leader 分配了1个任务项给这台机器来处理)时,将执行 this.createStrategyTask(strategy);
            while (list.size() < run.getRequestNum()) {
                IStrategyTask result = this.createStrategyTask(strategy);
                if (null == result) {
                    logger.error("strategy对应的配置有问题。strategyName=" + strategy.getStrategyName());
                }
                list.add(result);
            }
        }
    }

    /**
     * 终止一类任务，当strategyName==null时终止所有任务
     *
     * @param strategyName
     * @throws Exception
     */
    public void stopServer(String strategyName) throws Exception {
        if (strategyName == null) {
            String[] nameList = (String[]) this.managerMap.keySet().toArray(new String[0]);
            for (String name : nameList) {
                for (IStrategyTask task : this.managerMap.get(name)) {
                    try {
                        task.stop(strategyName);
                    } catch (Throwable e) {
                        logger.error("注销任务错误：strategyName=" + strategyName, e);
                    }
                }
                this.managerMap.remove(name);
            }
        } else {
            List<IStrategyTask> list = this.managerMap.get(strategyName);
            if (list != null) {
                for (IStrategyTask task : list) {
                    try {
                        task.stop(strategyName);
                    } catch (Throwable e) {
                        logger.error("注销任务错误：strategyName=" + strategyName, e);
                    }
                }
                this.managerMap.remove(strategyName);
            }

        }
    }

    /**
     * 停止所有调度资源
     */
    public void stopAll() throws Exception {
        try {
            lock.lock();
            this.start = false;
            if (this.initialThread != null) {
                this.initialThread.stopThread();
            }
            if (this.timer != null) {
                if (this.timerTask != null) {
                    this.timerTask.cancel();
                    this.timerTask = null;
                }
                this.timer.cancel();
                this.timer = null;
            }
            this.stopServer(null);
            if (this.zkManager != null) {
                this.zkManager.close();
            }
            if (this.scheduleStrategyManager != null) {
                try {
                    ZooKeeper zk = this.scheduleStrategyManager.getZooKeeper();
                    if (zk != null) {
                        zk.close();
                    }
                } catch (Exception e) {
                    logger.error("stopAll zk getZooKeeper异常！", e);
                }
            }
            this.uuid = null;
            logger.info("stopAll 停止服务成功！");
        } catch (Throwable e) {
            logger.error("stopAll 停止服务失败：" + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 重启所有的服务
     * @throws Exception
     */
    public void reStart() throws Exception {
        try {
            if (this.timer != null) {
                if (this.timerTask != null) {
                    this.timerTask.cancel();
                    this.timerTask = null;
                }
                this.timer.purge();
            }
            this.stopServer(null);
            if (this.zkManager != null) {
                this.zkManager.close();
            }
            this.uuid = null;
            this.init();
        } catch (Throwable e) {
            logger.error("重启服务失败：" + e.getMessage(), e);
        }
    }

    public boolean isZookeeperInitialSucess() throws Exception {
        return this.zkManager.checkZookeeperState();
    }

    public String[] getScheduleTaskDealList() {
        return applicationcontext.getBeanNamesForType(IScheduleTaskDeal.class);

    }

    public IScheduleDataManager getScheduleTaskManager() {
        if (this.scheduleTaskManager == null) {
            throw new RuntimeException(this.errorMessage);
        }
        return scheduleTaskManager;
    }

    public ScheduleStrategyDataManager4ZK getScheduleStrategyManager() {
        if (this.scheduleStrategyManager == null) {
            throw new RuntimeException(this.errorMessage);
        }
        return scheduleStrategyManager;
    }

    public void setApplicationContext(ApplicationContext aApplicationcontext) throws BeansException {
        applicationcontext = aApplicationcontext;
    }

    public Object getBean(String beanName) {
        return applicationcontext.getBean(beanName);
    }

    public String getUuid() {
        return uuid;
    }

    public String getIp() {
        return ip;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getHostName() {
        return hostName;
    }

    public void setStart(boolean isStart) {
        this.start = isStart;
    }

    public void setTimerInterval(int timerInterval) {
        this.timerInterval = timerInterval;
    }

    public void setZkConfig(Map<String, String> zkConfig) {
        this.zkConfig = zkConfig;
    }

    public ZKManager getZkManager() {
        return this.zkManager;
    }

    public Map<String, String> getZkConfig() {
        return zkConfig;
    }
}

class ManagerFactoryTimerTask extends java.util.TimerTask {
    private static transient Logger log = LoggerFactory.getLogger(ManagerFactoryTimerTask.class);
    TBScheduleManagerFactory factory;
    int count = 0;

    public ManagerFactoryTimerTask(TBScheduleManagerFactory aFactory) {
        this.factory = aFactory;
    }

    public void run() {
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            if (this.factory.zkManager.checkZookeeperState() == false) {
                if (count > 5) {
                    log.error("Zookeeper连接失败，关闭所有的任务后，重新连接Zookeeper服务器......");
                    this.factory.reStart();

                } else {
                    count = count + 1;
                }
            } else {
                count = 0;
                this.factory.refresh();
            }

        } catch (Throwable ex) {
            log.error(ex.getMessage(), ex);
        } finally {
            factory.timerTaskHeartBeatTS = System.currentTimeMillis();
        }
    }
}

class InitialThread extends Thread {
    private static transient Logger log = LoggerFactory.getLogger(InitialThread.class);
    TBScheduleManagerFactory facotry;
    boolean isStop = false;

    public InitialThread(TBScheduleManagerFactory aFactory) {
        this.facotry = aFactory;
    }

    public void stopThread() {
        this.isStop = true;
    }

    @Override
    public void run() {
        facotry.lock.lock();
        try {
            int count = 0;
            while (facotry.zkManager.checkZookeeperState() == false) {
                count = count + 1;
                if (count % 50 == 0) {
                    facotry.errorMessage = "Zookeeper connecting ......" + facotry.zkManager.getConnectStr() + " spendTime:" + count * 20 + "(ms)";
                    log.error(facotry.errorMessage);
                }
                Thread.sleep(20);
                if (this.isStop == true) {
                    return;
                }
            }
            facotry.initialData();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        } finally {
            facotry.lock.unlock();
        }

    }

}