
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