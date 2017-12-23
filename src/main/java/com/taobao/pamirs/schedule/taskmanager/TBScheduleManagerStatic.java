package com.taobao.pamirs.schedule.taskmanager;

import com.taobao.pamirs.schedule.TaskItemDefine;
import com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class TBScheduleManagerStatic extends TBScheduleManager {
    private static transient Logger logger = LoggerFactory.getLogger(TBScheduleManagerStatic.class);

    /**  任务项总数 */
    protected int taskItemCount = 0;

    //记录最新的版本号(取的是$rootPath/baseTaskType/$baseTaskType/$taskType/server节点的stat.version信息)
    protected long lastFetchVersion = -1;

    private final Object needReloadTaskItemLock = new Object();

    /**
     *
     * @param factory
     * @param baseTaskType
     * @param ownSign 默认值是BASE
     * @param scheduleTaskManager
     * @throws Exception
     */
    public TBScheduleManagerStatic(TBScheduleManagerFactory factory, String baseTaskType, String ownSign, IScheduleDataManager scheduleTaskManager) throws Exception {
        super(factory, baseTaskType, ownSign, scheduleTaskManager);
    }

    public void initialRunningInfo() throws Exception {
        //调度服务器节点$rootPath/baseTaskType/$baseTaskType/$taskType/server/$serverUuid最后修改时间超过了expireTime(来自于任务配置参数<假定服务死亡间隔(s),大家一般配置的是60s>)，就会被清除
        //这个节点被清除的后果是Timer(HeartBeatTimerTask 来自于<心跳频率(s) 大家一般配置的是5s>),发现这个节点（即线程组）不存在时会进行任务项的转移
        scheduleTaskManager.clearExpireScheduleServer(this.scheduleServer.getTaskType(), this.taskTypeInfo.getJudgeDeadInterval());

        //清理之后还存活的线程组收集起来存放到集合serverList中
        List<String> serverList = scheduleTaskManager.loadScheduleServerNames(this.scheduleServer.getTaskType());
        if (scheduleTaskManager.isLeader(this.scheduleServer.getUuid(), serverList)) {
            //是第一次启动，Leader先清除所有的垃圾数据
            logger.debug("程序第一次启动，Leader（"+this.scheduleServer.getUuid()+"）先清除taskItem目录,再重新创建");
            this.scheduleTaskManager.initialRunningInfo4Static(this.scheduleServer.getBaseTaskType(), this.scheduleServer.getOwnSign(), this.scheduleServer.getUuid());
        }
    }
    public void initial() throws Exception {
        new Thread(this.scheduleServer.getTaskType() + "-" + this.threadGroupNumber + "-StartProcess") {
            @SuppressWarnings("static-access")
            public void run() {
                try {
                    logger.info("开始获取调度任务队列...... of " + scheduleServer.getUuid());
                    while (isRuntimeInfoInitial == false) {
                        if (isStopSchedule == true) {
                            logger.debug("外部命令终止调度,退出调度队列获取：" + scheduleServer);
                            return;
                        }
                        //logger.error("isRuntimeInfoInitial = " + isRuntimeInfoInitial);
                        try {
                            initialRunningInfo();
                            isRuntimeInfoInitial = scheduleTaskManager.isInitialRunningInfoSucuss(
                                    scheduleServer.getBaseTaskType(),
                                    scheduleServer.getOwnSign());
                        } catch (Throwable e) {
                            //忽略初始化的异常
                            logger.error(e.getMessage(), e);
                        }
                        if (isRuntimeInfoInitial == false) {
                            Thread.currentThread().sleep(1000);
                        }
                    }
                    int count = 0;
                    lastReloadTaskItemListTime = scheduleTaskManager.getSystemTime();
                    while (getCurrentScheduleTaskItemListNow().size() <= 0) {
                        if (isStopSchedule == true) {
                            logger.debug("外部命令终止调度,退出调度队列获取：" + scheduleServer);
                            return;
                        }
                        Thread.currentThread().sleep(1000);
                        count = count + 1;
                        // logger.error("尝试获取调度队列，第" + count + "次 ") ;
                    }
                    String tmpStr = "";
                    for (int i = 0; i < currentTaskItemList.size(); i++) {
                        if (i > 0) {
                            tmpStr = tmpStr + ",";
                        }
                        tmpStr = tmpStr + currentTaskItemList.get(i);
                    }
                    logger.info("获取到任务处理队列，开始调度具体任务项[" + tmpStr + "]  of  " + scheduleServer);

                    //任务项总数
                    taskItemCount = scheduleTaskManager.loadAllTaskItem(scheduleServer.getTaskType()).size();
                    //只有在已经获取到任务处理队列后才开始启动任务处理器
                    computerStart();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    String str = e.getMessage();
                    if (str.length() > 300) {
                        str = str.substring(0, 300);
                    }
                    startErrorInfo = "启动处理异常：" + str;
                }
            }
        }.start();
    }

    /**
     * 定时向数据配置中心更新当前服务器的心跳信息。
     * 如果发现本次更新的时间如果已经超过了，服务器死亡的心跳周期，则不能在向服务器更新信息。
     * 而应该当作新的服务器，进行重新注册。
     * @throws Exception
     */
    public void refreshScheduleServerInfo() throws Exception {
        try {
            rewriteScheduleInfo();
            //如果任务信息没有初始化成功，不做任务相关的处理
            if (this.isRuntimeInfoInitial == false) {
                return;
            }

            //重新分配任务
            this.assignScheduleTask();

            //判断是否需要重新加载任务队列，避免任务处理进程不必要的检查和等待
            boolean tmpBoolean = this.isNeedReLoadTaskItemList();
            if (tmpBoolean != this.isNeedReloadTaskItem) {
                //只要不相同，就设置需要重新装载，因为在心跳异常的时候，做了清理队列的事情，恢复后需要重新装载。
                synchronized (needReloadTaskItemLock) {
                    this.isNeedReloadTaskItem = true;
                }
                rewriteScheduleInfo();
            }

            if (this.isPauseSchedule == true || this.processor != null && processor.isSleeping() == true) {
                //如果服务已经暂停了，则需要重新定时更新 cur_server 和 req_server
                //如果服务没有暂停，一定不能调用的
                this.getCurrentScheduleTaskItemListNow();
            }
        } catch (Throwable e) {
            //清除内存中所有的已经取得的数据和任务队列,避免心跳线程失败时候导致的数据重复
            this.clearMemoInfo();
            if (e instanceof Exception) {
                throw (Exception) e;
            } else {
                throw new Exception(e.getMessage(), e);
            }
        }
    }

    /**
     * 在leader重新分配任务，在每个server释放原来占有的任务项时，都会修改这个版本号
     * @return
     * @throws Exception
     */
    public boolean isNeedReLoadTaskItemList() throws Exception {
        return this.lastFetchVersion < this.scheduleTaskManager.getReloadTaskItemFlag(this.scheduleServer.getTaskType());
    }

    /**
     * 判断某个任务对应的线程组是否处于僵尸状态。
     * true 表示有线程组处于僵尸状态。需要告警。
     *
     * @param taskType
     * @param statMap
     * @return
     * @throws Exception
     */
    private boolean isExistZombieServ(String taskType, Map<String, Stat> statMap) throws Exception {
        boolean exist = false;
        for (String serverUuid : statMap.keySet()) {
            Stat s = statMap.get(serverUuid);
            if (this.scheduleTaskManager.getSystemTime() - s.getMtime() > this.taskTypeInfo.getHeartBeatRate() * 40) {
                logger.error("存在僵尸线程组! serverUuid=" + serverUuid + " ,taskType=" + taskType + "超过40次心跳周期未更新");
                exist = true;
            }
        }
        return exist;

    }

    /**
     * 根据当前调度服务器的信息，重新计算分配所有的调度任务
     * 任务的分配是需要加锁，避免数据分配错误。为了避免数据锁带来的负面作用，通过版本号来达到锁的目的
     *
     * 1、获取任务状态的版本号
     * 2、获取所有的服务器注册信息和任务队列信息
     * 3、清除已经超过心跳周期的服务器注册信息
     * 3、重新计算任务分配
     * 4、更新任务状态的版本号【乐观锁】
     * 5、根系任务队列的分配信息
     * @throws Exception
     */
    public void assignScheduleTask() throws Exception {
        scheduleTaskManager.clearExpireScheduleServer(this.scheduleServer.getTaskType(), this.taskTypeInfo.getJudgeDeadInterval());
        List<String> serverList = scheduleTaskManager.loadScheduleServerNames(this.scheduleServer.getTaskType());

        if (scheduleTaskManager.isLeader(this.scheduleServer.getUuid(), serverList) == false) {
            if (logger.isDebugEnabled()) {
                logger.debug("scheduleServer(" + this.scheduleServer.getUuid() + "):不是负责任务分配(" + this.scheduleServer.getTaskType() + ")的Leader,直接返回");
            }
            return;
        }

        //设置初始化成功标准，避免在leader转换的时候，新增的线程组初始化失败

        //1.在taskItem节点的data信息里记录当前Leader信息
        scheduleTaskManager.setInitialRunningInfoSucuss(this.scheduleServer.getBaseTaskType(), this.scheduleServer.getTaskType(), this.scheduleServer.getUuid());

        //2.检查每个任务项的处理机器cur_server，是否还处于存活状态，不存活则擦除掉
        scheduleTaskManager.clearTaskItem(this.scheduleServer.getTaskType(), serverList);

        //3.真正的给每个线程组serverUuid分配任务项
        scheduleTaskManager.assignTaskItem(this.scheduleServer.getTaskType(), this.scheduleServer.getUuid(), this.taskTypeInfo.getMaxTaskItemsOfOneThreadGroup(), serverList);
    }

    /**
     * 重新加载当前服务器的任务队列
     * 1、释放当前服务器持有，但有其它服务器进行申请的任务队列
     * 2、重新获取当前服务器的处理队列
     *
     * 为了避免此操作的过度，阻塞真正的数据处理能力。系统设置一个重新装载的频率。例如1分钟
     *
     * 特别注意：
     *   此方法的调用必须是在当前所有任务都处理完毕后才能调用，否则是否任务队列后可能数据被重复处理
     */

    public List<TaskItemDefine> getCurrentScheduleTaskItemList() {
        try {
            if (this.isNeedReloadTaskItem == true) {
                //特别注意：需要判断数据队列是否已经空了，否则可能在队列切换的时候导致数据重复处理
                //主要是在线程不休眠就加载数据的时候一定需要这个判断
                if (this.processor != null) {
                    while (this.processor.isDealFinishAllData() == false) {
                        Thread.sleep(50);
                    }
                }
                //真正开始处理数据
                synchronized (needReloadTaskItemLock) {
                    this.getCurrentScheduleTaskItemListNow();
                    this.isNeedReloadTaskItem = false;
                }
            }
            this.lastReloadTaskItemListTime = this.scheduleTaskManager.getSystemTime();
            return this.currentTaskItemList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //由于上面在数据执行时有使用到synchronized ，但是心跳线程并没有对应加锁。
    //所以在此方法上加一下synchronized。20151015
    protected synchronized List<TaskItemDefine> getCurrentScheduleTaskItemListNow() throws Exception {
        //如果已经稳定了，理论上不需要加载去扫描所有的叶子结点
        //20151019 by kongxuan.zlj
        try {
            Map<String, Stat> statMap = this.scheduleTaskManager.getCurrentServerStatList(this.scheduleServer.getTaskType());
            //server下面的机器节点的运行时环境是否在刷新，未刷新的话，打印告警日志
            isExistZombieServ(this.scheduleServer.getTaskType(), statMap);
        } catch (Exception e) {
            logger.error("zombie serverList exists， Exception:", e);
        }
        //记录最新的版本号(取的是$rootPath/baseTaskType/$baseTaskType/$taskType/server节点的stat.version信息)
        this.lastFetchVersion = this.scheduleTaskManager.getReloadTaskItemFlag(this.scheduleServer.getTaskType());
        logger.debug(" this.scheduleServer.getTaskType()=" + this.scheduleServer.getTaskType() + ",isNeedReloadTaskItem=" + isNeedReloadTaskItem);
        try {
            //是否被人申请的队列
            this.scheduleTaskManager.releaseDealTaskItem(this.scheduleServer.getTaskType(), this.scheduleServer.getUuid());

            //该方法仅仅是查询指定线程组serverUuid被分配的任务项而已,而不是进行真实的任务项分配
            //为了避免在休眠切换的过程中出现队列瞬间的不一致，先清除内存中的队列
            this.currentTaskItemList.clear();
            this.currentTaskItemList = this.scheduleTaskManager.reloadDealTaskItem(  this.scheduleServer.getTaskType(), this.scheduleServer.getUuid());

            //如果超过10个心跳周期还没有获取到调度队列，则报警
            if (this.currentTaskItemList.size() == 0 &&
                    scheduleTaskManager.getSystemTime() - this.lastReloadTaskItemListTime
                            > this.taskTypeInfo.getHeartBeatRate() * 20) {
                StringBuffer buf = new StringBuffer();
                buf.append("调度服务器");
                buf.append("[");
                buf.append(this.scheduleServer.toString());
                buf.append("]自启动以来，超过20个心跳周期，还没有获取到分配的任务队列;");
                buf.append("  currentTaskItemList.size() =" + currentTaskItemList.size());
                buf.append(" ,scheduleTaskManager.getSystemTime()=" + scheduleTaskManager.getSystemTime());
                buf.append(" ,lastReloadTaskItemListTime=" + lastReloadTaskItemListTime);
                buf.append(" ,taskTypeInfo.getHeartBeatRate()*20=" + taskTypeInfo.getHeartBeatRate() * 20);
                logger.error(buf.toString());
            }

            if (this.currentTaskItemList.size() > 0) {
                //更新时间戳
                this.lastReloadTaskItemListTime = scheduleTaskManager.getSystemTime();
            }

            return this.currentTaskItemList;
        } catch (Throwable e) {
            this.lastFetchVersion = -1; //必须把把版本号设置小，避免任务加载失败
            if (e instanceof Exception) {
                throw (Exception) e;
            } else {
                throw new Exception(e);
            }
        }
    }

    public int getTaskItemCount() {
        return this.taskItemCount;
    }

}
