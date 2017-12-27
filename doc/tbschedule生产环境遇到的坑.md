1台服务器，1个线程组，1个任务项，一开始是正常运行的，
执行一段时间后，这个任务项没有机器执行
原因分析：1个应用在worker平台配置有5个任务，研发启动的时候看到有任务在执行，以为就启动成功了，实际上有个别任务没有启动成功的，
worker平台配置有5个任务，代码中实际只有4个任务bean就可以造成这种情况（比如第3个任务bean代码中缺失，就会抛异常，造成第4，5个任务始终加载不成功）

8台服务器，8个线程组，8个任务项，一开始每台机器1个线程组1个任务项，
执行一段时间后，变成了只有7台机器参与执行这个任务，其中1台机器1个线程组2个任务项，其它6台机器6个线程组每个线程组1个任务项
另外的1台虽然拿不到任务项，但是却以100ms的速度重复执行selectTask方法
代码逻辑简化
1.#################################################
时间：[DEBUG 2017-12-22 15:51:29.963]
线程名称：[scanPushTaskSchedule_TASK-2-exe0]-代号线程X1
关键日志：[DEBUG 2017-12-22 15:51:29.963] [scanPushTaskSchedule_TASK-2-exe0] [com.taobao.pamirs.schedule.taskmanager.TBScheduleManager.pause(TBScheduleManager.java:296)] 暂停调度 ：scanPushTaskSchedule_TASK$10.190.17.166$4E71B6428C7D4D24B11ABADF71546A7F$0000000049:FetchDataCount=8574,FetchDataNum=0,DealDataSucess=0,DealDataFail=0,DealSpendTime=0,otherCompareCount=0
关键代码：
TBScheduleProcessorSleep
	run()-loadScheduleData()
	   		  taskDealBean.selectTasks()
	      当没有取到数据时，执行暂停
	      scheduleManager.isContinueWhenData()

    public boolean isContinueWhenData() throws Exception {
        if (isPauseWhenNoData() == true) {
            this.pause("没有数据,暂停调度");
            return false;
        } else {
            return true;
        }
    }

    public void pause(String message) throws Exception {
        if (this.isPauseSchedule == false) {
            this.isPauseSchedule = true;      ====================>会执行
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

    //暂停之后进行自我销毁，还有一段代码
    synchronized(this.threadList) {
        this.threadList.remove(Thread.currentThread());
        if(this.threadList.size() == 0) {
            this.scheduleManager.unRegisterScheduleServer();    =======提前预告下出问题的代码块，就是这里
        }

        return;
    }

2.#################################################
时间： [DEBUG 2017-12-22 15:51:30.001]
线程名称：[scanPushTaskSchedule_TASK-2-HeartBeat]-代号线程Y，
关键日志：[DEBUG 2017-12-22 15:51:30.001] [scanPushTaskSchedule_TASK-2-HeartBeat] [com.taobao.pamirs.schedule.taskmanager.TBScheduleManager.resume(TBScheduleManager.java:311)] 恢复调度:scanPushTaskSchedule_TASK$10.190.17.166$4E71B6428C7D4D24B11ABADF71546A7F$0000000049
关键代码：
    public void resume(String message) throws Exception {
        if(this.isPauseSchedule) {      ==============根据代码执行的轨迹，这里是满足条件的
            if(log.isDebugEnabled()) {
                log.debug("恢复调度:" + this.currenScheduleServer.getUuid());
            }

            this.isPauseSchedule = false;            ====================>会执行
            this.pauseMessage = message;
            if(this.taskDealBean != null) {
                if(this.taskTypeInfo.getProcessorType() != null && this.taskTypeInfo.getProcessorType().equalsIgnoreCase("NOTSLEEP")) {
                    this.taskTypeInfo.setProcessorType("NOTSLEEP");
                    this.processor = new TBScheduleProcessorNotSleep(this, this.taskDealBean, this.statisticsInfo);
                } else {
                    this.processor = new TBScheduleProcessorSleep(this, this.taskDealBean, this.statisticsInfo);
                    this.taskTypeInfo.setProcessorType("SLEEP");
                }
            }

            this.rewriteScheduleInfo();
        }

    }


3.#################################################
时间： [TRACE 2017-12-22 15:51:30.001]
线程名称：[scanPushTaskSchedule_TASK-2-exe0] -代号线程X1 , [scanPushTaskSchedule_TASK-2-exe0] -代号线程X2
关键日志：[TRACE 2017-12-22 15:51:30.001] [scanPushTaskSchedule_TASK-2-exe0] [com.taobao.pamirs.schedule.taskmanager.TBScheduleProcessorSleep.run(TBScheduleProcessorSleep.java:256)] scanPushTaskSchedule_TASK-2-exe0：当前运行线程数量:1 =====》这里是本次resume()方法唤醒执行的线程X1
[DEBUG 2017-12-22 15:51:30.022] [scanPushTaskSchedule_TASK-2-exe0] [com.taobao.pamirs.schedule.taskmanager.TBScheduleManager.unRegisterScheduleServer(TBScheduleManager.java:363)] 注销服务器 ：scanPushTaskSchedule_TASK$10.190.17.166$4E71B6428C7D4D24B11ABADF71546A7F$0000000049 =====》这里是上次resume()方法唤醒执行的线程X2（那为什么线程名字相同呢，是由于他们的生成规则造成的）

关键代码：
	synchronized(this.threadList) {
        this.threadList.remove(Thread.currentThread());
        if(this.threadList.size() == 0) {
            this.scheduleManager.unRegisterScheduleServer();    =======提前预告下出问题的代码块，就是这里，X1线程执行
        }

        return;
    }

    protected void unRegisterScheduleServer() throws Exception {
        this.registerLock.lock();

        try {
            if(this.processor != null) {
                this.processor = null;
            }

            if(!this.isPauseSchedule) {            ============由于第2次的resume方法执行造成了isPauseSchedule=false，也就造成了这个线程组被清理的缘故
                if(log.isDebugEnabled()) {
                    log.debug("注销服务器 ：" + this.currenScheduleServer.getUuid());
                }

                this.isStopSchedule = true;
                this.heartBeatTimer.cancel();
                this.scheduleCenter.unRegisterScheduleServer(this.currenScheduleServer.getTaskType(), this.currenScheduleServer.getUuid());
                return;
            }
        } finally {
            this.registerLock.unlock();
        }

    }


根本原因：过早的更改标志位TBScheduleManager.isPauseSchedule，造成了TBScheduleProcessorSleep在处理完成后进行资源清理时可能错误销毁线程组的情况
解决办法：
资源清理提供专有的方法来处理，比如
    protected void destoryProcessor() throws Exception {
        registerLock.lock();
        try {
            if (this.processor != null) {
                this.processor = null;
            }
        } finally {
            registerLock.unlock();
        }
    }

	synchronized(this.threadList) {
        this.threadList.remove(Thread.currentThread());
        if(this.threadList.size() == 0) {
            this.scheduleManager.destoryProcessor();
        }

        return;
    }



