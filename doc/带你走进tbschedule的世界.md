
废除配置项
分配的单线程组最大任务项数量 ScheduleTaskType.maxTaskItemsOfOneThreadGroup
分配的单JVM最大线程组数量(1项任务在1个房子<机器>里最多允许多少个团队来执行) ScheduleStrategy.numOfSingleServer

调度服务器节点$rootPath/baseTaskType/$baseTaskType/$taskType/server/$serverUuid最后修改时间超过了expireTime(来自于任务配置参数<假定服务死亡间隔(s),大家一般配置的是60s>)，就会被清除
这个节点被清除的后果是Timer(HeartBeatTimerTask 来自于<心跳频率(s) 大家一般配置的是5s>),发现这个节点（即线程组）不存在时会进行任务项的转移
   @Override
    public int clearExpireScheduleServer(String taskType, long expireTime) throws Exception {
        int result = 0;
        String baseTaskType = ScheduleUtil.splitBaseTaskTypeFromTaskType(taskType);
        String zkPath = this.PATH_BaseTaskType + "/" + baseTaskType  + "/" + taskType + "/" + this.PATH_Server;
        if (this.getZooKeeper().exists(zkPath, false) == null) {
            String tempPath = this.PATH_BaseTaskType + "/" + baseTaskType + "/" + taskType;
            if (this.getZooKeeper().exists(tempPath, false) == null) {
                this.getZooKeeper().create(tempPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
            }
            this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
        }
        for (String scheduleServer : this.getZooKeeper().getChildren(zkPath, false)) {
            try {
                Stat stat = this.getZooKeeper().exists(zkPath + "/" + scheduleServer, false);
                if (getSystemTime() - stat.getMtime() > expireTime) {
                    logger.info("清除过期的scheduleServer=" + zkPath + "/" + scheduleServer);
                    ZKTools.deleteTree(this.getZooKeeper(), zkPath + "/" + scheduleServer);
                    result++;
                }

CopyOnWriteArrayList的原理
protected List<TaskItemDefine> currentTaskItemList = new CopyOnWriteArrayList<TaskItemDefine>();

threadNum-每个线程组分配的线程数量
Spring bean(IScheduleTaskDealMulti，IScheduleTaskDealSingle)其中的 selectTasks(...)会由单线程来处理
另外的execute(...)会是多线程来处理

?????????
任务配置
每10秒执行1次 0/10 * * * * *
如果10:00:00秒执行了1次，用了68秒，那么这个期间，10:00:10,10:00:20,10:00:30任务会触发会执行么
TBScheduleManager.resume() 会被触发，但是真实的任务不会执行，原因在于这个变量TBScheduleManager.isPauseSchedule


