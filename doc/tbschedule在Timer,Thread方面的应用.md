
zk = new ZooKeeper(……）
这行代码会隐式创建两个线程
    sendThread = new SendThread(clientCnxnSocket);
    eventThread = new EventThread();

InitialThread
启动的时候异步化操作，避免阻塞整个应用程序的启动过程，异步化的操作全部封装在这个方法里
public void initialData() throws Exception {
    this.zkManager.initial(); //保证$rootpath存在，没有则创建
    this.scheduleDataManager = new ScheduleDataManager4ZK(this.zkManager); //保证$rootpath/baseTaskType存在，没有则创建 ，并记录zk服务器上的时间zkBaseTime
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

timer = new Timer("TBScheduleManagerFactory-Timer");
timerTask = new ManagerFactoryTimerTask(this);
timer.schedule(timerTask, 2000, this.timerInterval);  // timerInterval = 2000;

这个task(ManagerFactoryTimerTask)每2秒执行1次
this.factory.refresh();


this.heartBeatTimer = new Timer(this.scheduleServer.getTaskType() + "-" + this.threadGroupNumber + "-HeartBeatTimer");
this.heartBeatTimer.schedule(new HeartBeatTimerTask(this), new Date(System.currentTimeMillis() + 500),this.taskTypeInfo.getHeartBeatRate());

这里只需要heartBeatTimer帮忙在指定时间点执行一次，触发后在PauseOrResumeScheduleTask.run()方法中调用了this.cancel();又将自己清除了，
重新计算下次执行时间后，又再次调用this.timer.schedule(new PauseOrResumeScheduleTask(this.manager, this.timer, this.type, this.cronTabExpress), nextTime);重新加入到任务队列了
this.heartBeatTimer.schedule(
                new PauseOrResumeScheduleTask(this, this.heartBeatTimer, PauseOrResumeScheduleTask.TYPE_RESUME, tmpStr),
                firstStartTime);
this.heartBeatTimer.schedule(
            new PauseOrResumeScheduleTask(this, this.heartBeatTimer,
                    PauseOrResumeScheduleTask.TYPE_PAUSE, tmpEndStr),
            firstEndTime);


   public PauseOrResumeScheduleTask(TBScheduleManager aManager, Timer aTimer, int aType, String aCronTabExpress) {
        this.manager = aManager;
        this.timer = aTimer;
        this.type = aType;
        this.cronTabExpress = aCronTabExpress;
    }

    public void run() {
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            this.cancel();//取消调度任务
            Date current = new Date(System.currentTimeMillis());
            CronExpression cexp = new CronExpression(this.cronTabExpress);
            Date nextTime = cexp.getNextValidTimeAfter(current);
            if (this.type == TYPE_PAUSE) {
                manager.pause("到达终止时间,pause调度");
                this.manager.getScheduleServer().setNextRunEndTime(ScheduleUtil.transferDataToString(nextTime));
            } else {
                manager.resume("到达开始时间,resume调度");
                this.manager.getScheduleServer().setNextRunStartTime(ScheduleUtil.transferDataToString(nextTime));
            }
            this.timer.schedule(new PauseOrResumeScheduleTask(this.manager, this.timer, this.type, this.cronTabExpress), nextTime);
        } catch (Throwable ex) {
            log.error(ex.getMessage(), ex);
        }
    }
}