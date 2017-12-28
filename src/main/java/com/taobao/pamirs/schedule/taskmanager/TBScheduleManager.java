package com.taobao.pamirs.schedule.taskmanager;

import com.taobao.pamirs.schedule.CronExpression;
import com.taobao.pamirs.schedule.IScheduleTaskDeal;
import com.taobao.pamirs.schedule.ScheduleUtil;
import com.taobao.pamirs.schedule.TaskItemDefine;
import com.taobao.pamirs.schedule.strategy.IStrategyTask;
import com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 1、任务调度分配器的目标：	让所有的任务不重复，不遗漏的被快速处理。
 * 2、一个Manager只管理一种任务类型的一组工作线程。
 * 3、在一个JVM里面可能存在多个处理相同任务类型的Manager，也可能存在处理不同任务类型的Manager。
 * 4、在不同的JVM里面可以存在处理相同任务的Manager 
 * 5、调度的Manager可以动态的随意增加和停止
 *
 * 主要的职责：
 * 1、定时向集中的数据配置中心更新当前调度服务器的心跳状态
 * 2、向数据配置中心获取所有服务器的状态来重新计算任务的分配。这么做的目标是避免集中任务调度中心的单点问题。
 * 3、在每个批次数据处理完毕后，检查是否有其它处理服务器申请自己把持的任务队列，如果有，则释放给相关处理服务器。
 *
 * 其它：
 * 	 如果当前服务器在处理当前任务的时候超时，需要清除当前队列，并释放已经把持的任务。并向控制主动中心报警。
 *
 * @author xuannan
 *
 */
@SuppressWarnings({"rawtypes", "unchecked"})
abstract class TBScheduleManager implements IStrategyTask {
    private static transient Logger logger = LoggerFactory.getLogger(TBScheduleManager.class);
    /**
     * 用户标识不同线程的序号
     */
    private static int nextSerialNumber = 0;
    /**
     * 当前线程组编号
     */
    protected int threadGroupNumber = 0;

    /**
     * 调度任务类型信息
     */
    protected ScheduleTaskType taskTypeInfo;
    /**
     * 当前调度服务的信息
     */
    protected ScheduleServer scheduleServer;
    /**
     * 队列处理器
     */
    IScheduleTaskDeal taskDealBean;
    /**
     *  当前线程组分配的任务项列表
     *  ArrayList实现不是同步的。因多线程操作修改该列表，会造成ConcurrentModificationException
     */
    protected List<TaskItemDefine> currentTaskItemList = new CopyOnWriteArrayList<TaskItemDefine>();

    //    private String mBeanName;
    /**
     * 向配置中心更新信息的定时器
     */
    private Timer heartBeatTimer;

    /**
     * 多线程任务处理器
     */
    IScheduleProcessor processor;
    StatisticsInfo statisticsInfo = new StatisticsInfo();

    boolean isPauseSchedule = true;
    protected boolean isStopSchedule = false;


    String pauseMessage = "";

    /**
     * 最近一起重新装载调度任务的时间[ZK服务器时间]
     * 当前实际  - 上此装载时间  > intervalReloadTaskItemList，则向配置中心请求最新的任务分配情况
     */
    protected long lastReloadTaskItemListTime = 0;
    protected boolean isNeedReloadTaskItem = true;


    protected IScheduleDataManager scheduleTaskManager;

    protected String startErrorInfo = null;


    protected Lock registerLock = new ReentrantLock();

    /**
     * 运行期信息是否初始化成功
     */
    protected boolean isRuntimeInfoInitial = false;

    TBScheduleManagerFactory factory;

    /**
     *
     * @param factory
     * @param baseTaskType
     * @param ownSign 默认值是BASE
     * @param scheduleTaskManager
     * @throws Exception
     */
    TBScheduleManager(TBScheduleManagerFactory factory, String baseTaskType, String ownSign, IScheduleDataManager scheduleTaskManager) throws Exception {
        this.factory = factory;
        this.threadGroupNumber = serialNumber();
        this.scheduleTaskManager = scheduleTaskManager;
        this.taskTypeInfo = this.scheduleTaskManager.loadTaskTypeBaseInfo(baseTaskType);
        logger.info("create TBScheduleManager for taskType:" + baseTaskType);
        //清除已经过期1天的TASK,OWN_SIGN的组合。超过一天没有活动的server视为过期
        //this.scheduleTaskManager.clearExpireTaskTypeRunningInfo(baseTaskType, ScheduleUtil.getLocalIP() + "清除过期server", this.taskTypeInfo.getExpireOwnSignInterval());
        this.scheduleTaskManager.clearExpireTaskTypeRunningInfo(baseTaskType, "", this.taskTypeInfo.getExpireOwnSignInterval());

        Object dealBean = factory.getBean(this.taskTypeInfo.getDealBeanName());
        if (dealBean == null) {
            throw new Exception("SpringBean " + this.taskTypeInfo.getDealBeanName() + " 不存在");
        }
        if (dealBean instanceof IScheduleTaskDeal == false) {
            throw new Exception("SpringBean " + this.taskTypeInfo.getDealBeanName() + " 没有实现 IScheduleTaskDeal接口");
        }
        this.taskDealBean = (IScheduleTaskDeal) dealBean;

        if (this.taskTypeInfo.getJudgeDeadInterval() < this.taskTypeInfo.getHeartBeatRate() * 5) {
            throw new Exception("数据配置["+taskTypeInfo.getBaseTaskType()+"]存在问题，死亡的时间间隔，至少要大于心跳线程的5倍。当前配置数据：" +
                    "judgeDeadInterval = " + this.taskTypeInfo.getJudgeDeadInterval() +
                    ",heartBeatRate = " + this.taskTypeInfo.getHeartBeatRate());
        }
        this.scheduleServer = ScheduleServer.createScheduleServer(this.scheduleTaskManager, baseTaskType, ownSign, this.taskTypeInfo.getThreadNumber());
        this.scheduleServer.setManagerFactoryUUID(this.factory.getUuid()); //给currenScheduleServer追加赋值 factoryUuid
        this.scheduleTaskManager.registerScheduleServer(this.scheduleServer);
//        this.mBeanName = "pamirs:name=" + "schedu le.ServerMananger." + this.scheduleServer.getUuid();
        this.heartBeatTimer = new Timer(this.scheduleServer.getTaskType() + "-" + this.threadGroupNumber + "-HeartBeatTimer");
        this.heartBeatTimer.schedule(new HeartBeatTimerTask(this), new Date(System.currentTimeMillis() + 500),this.taskTypeInfo.getHeartBeatRate());
        initial();
    }

    /**
     * 对象创建时需要做的初始化工作
     *
     * @throws Exception
     */
    public abstract void initial() throws Exception;

    public abstract void refreshScheduleServerInfo() throws Exception;

    public abstract void assignScheduleTask() throws Exception;

    public abstract List<TaskItemDefine> getCurrentScheduleTaskItemList();

    public abstract int getTaskItemCount();

    public String getTaskType() {
        return this.scheduleServer.getTaskType();
    }

    public void initialTaskParameter(String strategyName, String taskParameter) {
        //没有实现的方法，需要的参数直接从任务配置中读取
    }

    private static synchronized int serialNumber() {
        return nextSerialNumber++;
    }

    public int getThreadGroupNumber() {
        return this.threadGroupNumber;
    }

    /**
     * 清除内存中所有的已经取得的数据和任务队列,在心态更新失败，或者发现注册中心的调度信息被删除
     */
    public void clearMemoInfo() {
        try {
            // 清除内存中所有的已经取得的数据和任务队列,在心态更新失败，或者发现注册中心的调度信息被删除
            this.currentTaskItemList.clear();
            if (this.processor != null) {
                this.processor.clearAllHasFetchData();
            }
        } finally {
            //设置内存里面的任务数据需要重新装载
            this.isNeedReloadTaskItem = true;
        }

    }

    /**
     * 更新当前线程组节点信息$rootPath/baseTaskType/$baseTaskType/$taskType/server/$serverUuid data信息
     * 1.dealInfoDesc
     * 2.heartBeatTime
     * 3.version
     *
     * TODO:由于this.assignScheduleTask()方法可能会清理已经死亡的线程组节点，所以如果节点被删除后，就重新注册scheduleServer
     *
     * @throws Exception
     */
    public void rewriteScheduleInfo() throws Exception {
        registerLock.lock();
        try {
            if (this.isStopSchedule == true) {
                if (logger.isDebugEnabled()) {
                    logger.debug("外部命令终止调度,不在注册调度服务，避免遗留垃圾数据：" + scheduleServer.getUuid());
                }
                return;
            }
            //先发送心跳信息
            if (startErrorInfo == null) {
                this.scheduleServer.setDealInfoDesc(this.pauseMessage + ":" + this.statisticsInfo.getDealDescription());
            } else {
                this.scheduleServer.setDealInfoDesc(startErrorInfo);
            }
            if (this.scheduleTaskManager.refreshScheduleServer(this.scheduleServer) == false) {
                //更新信息失败，清除内存数据后重新注册
                this.clearMemoInfo();
                this.scheduleTaskManager.registerScheduleServer(this.scheduleServer);
            }
        } finally {
            registerLock.unlock();
        }
    }


    /**
     * 开始的时候，计算第一次执行时间
     * @throws Exception
     */
    public void computerStart() throws Exception {
        //只有当存在可执行队列后再开始启动队列
        boolean isRunNow = false;
        if (this.taskTypeInfo.getPermitRunStartTime() == null) {
            isRunNow = true;
        } else {
            String tmpStr = this.taskTypeInfo.getPermitRunStartTime();
            if (tmpStr.toLowerCase().startsWith("startrun:")) {
                isRunNow = true;
                tmpStr = tmpStr.substring("startrun:".length());
            }
            CronExpression cexpStart = new CronExpression(tmpStr);
            Date current = new Date(this.scheduleTaskManager.getSystemTime());
            Date firstStartTime = cexpStart.getNextValidTimeAfter(current);
            this.heartBeatTimer.schedule(
                    new PauseOrResumeScheduleTask(this, this.heartBeatTimer, PauseOrResumeScheduleTask.TYPE_RESUME, tmpStr),
                    firstStartTime);
            this.scheduleServer.setNextRunStartTime(ScheduleUtil.transferDataToString(firstStartTime));
            if (this.taskTypeInfo.getPermitRunEndTime() == null
                    || this.taskTypeInfo.getPermitRunEndTime().equals("-1")) {
                this.scheduleServer.setNextRunEndTime("当不能获取到数据的时候pause");
            } else {
                try {
                    String tmpEndStr = this.taskTypeInfo.getPermitRunEndTime();
                    CronExpression cexpEnd = new CronExpression(tmpEndStr);
                    Date firstEndTime = cexpEnd.getNextValidTimeAfter(firstStartTime);
                    Date nowEndTime = cexpEnd.getNextValidTimeAfter(current);
                    if (!nowEndTime.equals(firstEndTime) && current.before(nowEndTime)) {
                        isRunNow = true;
                        firstEndTime = nowEndTime;
                    }
                    this.heartBeatTimer.schedule(
                            new PauseOrResumeScheduleTask(this, this.heartBeatTimer,
                                    PauseOrResumeScheduleTask.TYPE_PAUSE, tmpEndStr),
                            firstEndTime);
                    this.scheduleServer.setNextRunEndTime(ScheduleUtil.transferDataToString(firstEndTime));
                } catch (Exception e) {
                    logger.error("计算第一次执行时间出现异常:" + scheduleServer.getUuid(), e);
                    throw new Exception("计算第一次执行时间出现异常:" + scheduleServer.getUuid(), e);
                }
            }
        }
        if (isRunNow == true) {
            this.resume("开启服务立即启动");
        }
        this.rewriteScheduleInfo();

    }

    /**
     * 当Process没有获取到数据的时候调用，决定是否暂时停止服务器
     * @throws Exception
     */
    public boolean isContinueWhenData() throws Exception {
        if (isPauseWhenNoData() == true) {
            this.pause("没有数据,暂停调度");
            return false;
        } else {
            return true;
        }
    }

    /**
     * 判断是否暂停本次任务调度（仅仅影响当前这个时间周期，当到达下一个时间触发点时仍然可以执行）
     *
     *
     * @return
     */
    public boolean isPauseWhenNoData() {
        //如果还没有分配到任务队列则不能退出
        if (this.currentTaskItemList.size() > 0 && this.taskTypeInfo.getPermitRunStartTime() != null) {
            if (this.taskTypeInfo.getPermitRunEndTime() == null
                    || this.taskTypeInfo.getPermitRunEndTime().equals("-1")) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * 超过运行的运行时间，暂时停止调度
     * @throws Exception
     */
    public void pause(String message) throws Exception {
        if (this.isPauseSchedule == false) {
            this.isPauseSchedule = true;
            this.pauseMessage = message;
            if (logger.isDebugEnabled()) {
                logger.debug("暂停调度 ：" + this.scheduleServer.getUuid() + ":" + this.statisticsInfo.getDealDescription());
            }
            if (this.processor != null) {
                this.processor.stopSchedule();
            }
            rewriteScheduleInfo();
        }
    }

    /**
     * 处在了可执行的时间区间，恢复运行
     * @throws Exception
     */
    public void resume(String message) throws Exception {
        if (this.isPauseSchedule == true) {
            if (logger.isDebugEnabled()) {
                logger.debug("恢复调度:" + this.scheduleServer.getUuid());
            }
            this.isPauseSchedule = false;
            this.pauseMessage = message;
            if (this.taskDealBean != null) {
                if (this.taskTypeInfo.getProcessorType() != null &&
                        this.taskTypeInfo.getProcessorType().equalsIgnoreCase("NOTSLEEP") == true) {
                    this.taskTypeInfo.setProcessorType("NOTSLEEP");
                    this.processor = new TBScheduleProcessorNotSleep(this,
                            taskDealBean, this.statisticsInfo);
                } else {
                    this.processor = new TBScheduleProcessorSleep(this,
                            taskDealBean, this.statisticsInfo);
                    this.taskTypeInfo.setProcessorType("SLEEP");
                }
            }
            rewriteScheduleInfo();
        }
    }

    /**
     * 当服务器停止的时候，调用此方法清除所有未处理任务，清除服务器的注册信息。
     * 也可能是控制中心发起的终止指令。
     * 需要注意的是，这个方法必须在当前任务处理完毕后才能执行
     * @throws Exception
     */
    public void stop(String strategyName) throws Exception {
        if (logger.isInfoEnabled()) {
            logger.info("停止服务器 ：" + this.scheduleServer.getUuid());
        }
        this.isPauseSchedule = false; //不是暂停，是停止 heartBeatTimer
        if (this.processor != null) {
            this.processor.stopSchedule();
        } else {
            this.unRegisterScheduleServer();
        }
    }

    /**
     * 支持处理 暂停<isPauseSchedule> 与 注销<isStopSchedule> 2种情况
     *
     * 只应该在Processor中调用
     * @throws Exception
     */
    protected void unRegisterScheduleServer() throws Exception {
        registerLock.lock();
        try {
            if (this.processor != null) {
                this.processor = null;
            }
            if (this.isPauseSchedule == true) {
                // 是暂停调度，不注销Manager自己
                return;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("注销服务器 ：" + this.scheduleServer.getUuid());
            }
            this.isStopSchedule = true;
            // 取消心跳TIMER
            this.heartBeatTimer.cancel();
            // 从配置中心注销自己
            this.scheduleTaskManager.unRegisterScheduleServer(this.scheduleServer.getTaskType(), this.scheduleServer.getUuid());
        } finally {
            registerLock.unlock();
        }
    }

    protected void unRegisterProcessor() throws Exception {
        registerLock.lock();
        try {
            if (this.processor != null) {
                this.processor = null;
            }
        } finally {
            registerLock.unlock();
        }
    }

    public ScheduleTaskType getTaskTypeInfo() {
        return taskTypeInfo;
    }


    public StatisticsInfo getStatisticsInfo() {
        return statisticsInfo;
    }

    /**
     * 打印给定任务类型的任务分配情况
     * @param taskType
     */
    public void printScheduleServerInfo(String taskType) {

    }

    public ScheduleServer getScheduleServer() {
        return this.scheduleServer;
    }

//    public String getmBeanName() {
//        return mBeanName;
//    }
}

class HeartBeatTimerTask extends java.util.TimerTask {
    private static transient Logger log = LoggerFactory.getLogger(HeartBeatTimerTask.class);
    TBScheduleManager scheduleManager;

    public HeartBeatTimerTask(TBScheduleManager aManager) {
        scheduleManager = aManager;
    }

    public void run() {
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            scheduleManager.refreshScheduleServerInfo();
        } catch (Exception ex) {
            log.error("HeartBeatTimerTask出现异常", ex);
        }
    }
}

class PauseOrResumeScheduleTask extends java.util.TimerTask {
    private static transient Logger log = LoggerFactory
            .getLogger(HeartBeatTimerTask.class);
    public static int TYPE_PAUSE = 1;
    public static int TYPE_RESUME = 2;
    TBScheduleManager scheduleManager;
    Timer timer;
    int type;
    String cronTabExpress;

    public PauseOrResumeScheduleTask(TBScheduleManager aManager, Timer aTimer, int aType, String aCronTabExpress) {
        this.scheduleManager = aManager;
        this.timer = aTimer;
        this.type = aType;
        this.cronTabExpress = aCronTabExpress;
    }

    public void run() {
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            this.cancel();//取消调度任务，//TODO:这一步是否有点多余，正常情况下timer是会自动删除掉这个一次性任务的
            Date current = new Date(System.currentTimeMillis());
            CronExpression cexp = new CronExpression(this.cronTabExpress);
            Date nextTime = cexp.getNextValidTimeAfter(current);
            if (this.type == TYPE_PAUSE) {
                scheduleManager.pause("到达终止时间,pause调度");
                this.scheduleManager.getScheduleServer().setNextRunEndTime(ScheduleUtil.transferDataToString(nextTime));
            } else {
                scheduleManager.resume("到达开始时间,resume调度");
                this.scheduleManager.getScheduleServer().setNextRunStartTime(ScheduleUtil.transferDataToString(nextTime));
            }
            this.timer.schedule(new PauseOrResumeScheduleTask(this.scheduleManager, this.timer, this.type, this.cronTabExpress), nextTime);
        } catch (Throwable ex) {
            log.error(ex.getMessage(), ex);
        }
    }
}

class StatisticsInfo {
    private AtomicLong fetchDataNum = new AtomicLong(0);//读取次数
    private AtomicLong fetchDataCount = new AtomicLong(0);//读取的数据量
    private AtomicLong dealDataSucess = new AtomicLong(0);//处理成功的数据量
    private AtomicLong dealDataFail = new AtomicLong(0);//处理失败的数据量
    private AtomicLong dealSpendTime = new AtomicLong(0);//处理总耗时,没有做同步，可能存在一定的误差
    private AtomicLong otherCompareCount = new AtomicLong(0);//特殊比较的次数

    public void addFetchDataNum(long value) {
        this.fetchDataNum.addAndGet(value);
    }

    public void addFetchDataCount(long value) {
        this.fetchDataCount.addAndGet(value);
    }

    public void addDealDataSucess(long value) {
        this.dealDataSucess.addAndGet(value);
    }

    public void addDealDataFail(long value) {
        this.dealDataFail.addAndGet(value);
    }

    public void addDealSpendTime(long value) {
        this.dealSpendTime.addAndGet(value);
    }

    public void addOtherCompareCount(long value) {
        this.otherCompareCount.addAndGet(value);
    }

    public String getDealDescription() {
        return "fetchDataCount=" + this.fetchDataCount
                + ",fetchDataNum=" + this.fetchDataNum
                + ",dealDataSucess=" + this.dealDataSucess
                + ",dealDataFail=" + this.dealDataFail
                + ",dealSpendTime=" + this.dealSpendTime
                + ",otherCompareCount=" + this.otherCompareCount;
    }

}