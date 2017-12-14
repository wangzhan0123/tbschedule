tbschedule源码解读

程序入口
com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory.init()

	this.zkManager = new ZKManager(p);
		this.zk = new ZooKeeper(...)
		    sendThread = new SendThread(clientCnxnSocket);  线程
       		eventThread = new EventThread();  线程

	this.initialThread = new InitialThread(this);  线程
		this.facotry.initialData();

=>
com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory.initialData()	
	this.zkManager.initial();
		ZKTools.createPath(this.zk, this.getRootPath(), CreateMode.PERSISTENT, this.acl);      //创建根节点
		this.zk.setData(this.getRootPath(), Version.getVersion().getBytes(), -1);

	this.scheduleDataManager = new ScheduleDataManager4ZK(this.zkManager);	
	    this.loclaBaseTime = System.currentTimeMillis();
	    String tempPath = this.zkManager.getZooKeeper().create(this.zkManager.getRootPath() + "/systime", (byte[])null, this.zkManager.getAcl(), CreateMode.EPHEMERAL_SEQUENTIAL);
	    Stat tempStat = this.zkManager.getZooKeeper().exists(tempPath, false);
	    this.zkBaseTime = tempStat.getCtime();
	    ZKTools.deleteTree(this.getZooKeeper(), tempPath);

	this.scheduleStrategyManager = new ScheduleStrategyDataManager4ZK(this.zkManager); 
		this.PATH_Strategy = this.zkManager.getRootPath() + "/strategy";              //创建 $rootPath/$strategy  CreateMode.PERSISTENT
        this.PATH_ManagerFactory = this.zkManager.getRootPath() + "/factory";          //创建 $rootPath/$factory  CreateMode.PERSISTENT
        ZKTools.createPath(this.getZooKeeper(), this.PATH_Strategy, CreateMode.PERSISTENT, this.zkManager.getAcl());
        ZKTools.createPath(this.getZooKeeper(), this.PATH_ManagerFactory, CreateMode.PERSISTENT, this.zkManager.getAcl());

    this.scheduleStrategyManager.registerManagerFactory(this);
		result = managerFactory.getIp() + "$" + managerFactory.getHostName() + "$" + UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
        String i$ = this.PATH_ManagerFactory + "/" + result + "$";     
         //创建 $rootPath/$factory/$uuid CreateMode.EPHEMERAL_SEQUENTIAL
        i$ = this.getZooKeeper().create(i$, (byte[])null, this.zkManager.getAcl(), CreateMode.EPHEMERAL_SEQUENTIAL);    
        managerFactory.setUuid(i$.substring(i$.lastIndexOf("/") + 1));

    this.timer = new Timer("TBScheduleManagerFactory-Timer");      Timer-线程,每2秒执行1次
        this.timerTask = new ManagerFactoryTimerTask(this); 
        this.timer.schedule(this.timerTask, 2000L, (long)this.timerInterval);
	=>
	new ManagerFactoryTimerTask(this);
		this.factory.refresh();         //这个方法会每2秒执行1次哟

=>
com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory.refresh()	
	stsInfo = this.getScheduleStrategyManager().loadManagerFactoryInfo(this.getUuid());
		//节点举例 /o2oworkerpath/o2o-message-center/factory/10.187.58.131$host-10-187-58-131$6F334D0CADE24D5B9534670911F97E38$0000000440
		String zkPath = this.PATH_ManagerFactory + "/" + uuid;
		//节点data,可能为null(null时代表true),也可能为true,false
		byte[] value = this.getZooKeeper().getData(zkPath, false, (Stat)null);            
        ManagerFactoryInfo result = new ManagerFactoryInfo();
        result.setUuid(uuid);
        if(value == null) {
            result.setStart(true);
        } else {
            result.setStart(Boolean.parseBoolean(new String(value)));
        }
    this.reRegisterManagerFactory();
  		List stopList = this.getScheduleStrategyManager().registerManagerFactory(this);
	  		result = this.PATH_ManagerFactory + "/" + managerFactory.getUuid();
	        if(this.getZooKeeper().exists(result, false) == null) {
	            // 创建 $rootPath/$factory/$uuid CreateMode.EPHEMERAL
	            //分布式情况下，每个机器各自创建各自的机器节点
	            this.getZooKeeper().create(result, (byte[])null, this.zkManager.getAcl(), CreateMode.EPHEMERAL);
	        }
			Iterator var12 = this.loadAllScheduleStrategy().iterator();
				String zkPath = this.PATH_Strategy;
		        ArrayList result = new ArrayList();
		        //查找$rootPath/$strategy 孩子节点,并排序
		        List names = this.getZooKeeper().getChildren(zkPath, false);
 				Collections.sort(names);

			while(var12.hasNext()) {
			        ScheduleStrategy scheduleStrategy = (ScheduleStrategy)var12.next();
			        boolean isFind = false;

			        // 这里的getIPList()源自于配置参数(运行IP：)
			        if(!ScheduleStrategy.STS_PAUSE.equalsIgnoreCase(scheduleStrategy.getSts()) && scheduleStrategy.getIPList() != null) {
			            String[] zkPath = scheduleStrategy.getIPList();
			            int len$ = zkPath.length;

			            for(int i$1 = 0; i$1 < len$; ++i$1) {
			                String ip = zkPath[i$1];
			                if(ip.equals("127.0.0.1") || ip.equalsIgnoreCase("localhost") || ip.equals(managerFactory.getIp()) || ip.equalsIgnoreCase(managerFactory.getHostName())) {
			                    String zkPath1 = this.PATH_Strategy + "/" + scheduleStrategy.getStrategyName() + "/" + managerFactory.getUuid();
			                    if(this.getZooKeeper().exists(zkPath1, false) == null) {

			                        //这个任务最终会有几个机器来参与执行,创建 $rootPath/$strategy/$strategyName/$uuid CreateMode.EPHEMERAL
			                        //分布式情况下，每个机器各自创建各自的机器节点
			                        this.getZooKeeper().create(zkPath1, (byte[])null, this.zkManager.getAcl(), CreateMode.EPHEMERAL);
			                    }

			                    isFind = true;
			                    break;
			                }
			            }
			        }

			    // 如果当前机器不满足ipList规则，就把刚刚创建的临时节点再删除掉，删除 $rootPath/$factory/$uuid CreateMode.EPHEMERAL
			    if(!isFind) {
	                String var13 = this.PATH_Strategy + "/" + scheduleStrategy.getStrategyName() + "/" + managerFactory.getUuid();
	                if(this.getZooKeeper().exists(var13, false) != null) {
	                    ZKTools.deleteTree(this.getZooKeeper(), var13);
	                    var11.add(scheduleStrategy.getStrategyName());
	                }
	            }
        //到目前为止，每个任务可能参与的机器均添加进来了，存储节点在 $rootPath/$strategy/$strategyName/$uuid CreateMode.EPHEMERAL
        //使用的是临时节点，也就是说某个机器如果挂了，就会从这里消失掉，后续将不在参与这个任务的执行
        //如果某个新机器加入进来了，也会创建一个节点，这个新机器会在后面给其分配任务项

	    // 下面的this=com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory     
        this.assignScheduleServer();
        this.reRunScheduleServer();
    	=>
com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory.assignScheduleServer();
	//得到当前机器可能参与执行的任务清单
	//为什么说是可能参与呢：比如说现在是10台机器，但是任务配置是8个线程组，那么最终将会有2台机器不会执行这个任务的
	Iterator i$ = this.scheduleStrategyManager.loadAllScheduleStrategyRunntimeByUUID(this.uuid).iterator();

		//遍历每个任务
		ScheduleStrategyRunntime run = (ScheduleStrategyRunntime)i$.next();
        List factoryList = this.scheduleStrategyManager.loadAllScheduleStrategyRunntimeByTaskType(run.getStrategyName());
        	//得到当前任务下的所有可能参与的机器，并排序，编号最小的排在uuidList前面
	        List uuidList = this.getZooKeeper().getChildren(zkPath + "/" + strategyName, false);
	            Collections.sort(uuidList, new Comparator() {
	                public int compare(String u1, String u2) {
	                    return u1.substring(u1.lastIndexOf("$") + 1).compareTo(u2.substring(u2.lastIndexOf("$") + 1));
	                }
	            });
	    //判断当前机器uuid是不是Leader(Leader的规则：uuid最小的那个；factoryList的第1个就是uuid最小的那个)
		if(factoryList.size() != 0 && this.isLeader(this.uuid, factoryList)) {
			//这里面的代码只有Leader才会执行
			ScheduleStrategy scheduleStrategy = this.scheduleStrategyManager.loadStrategy(run.getStrategyName());
             
			//数据格式：获取任务的一部分配置信息
			$rootPath/$strategy/$strategyName
			[{
			    "strategyName":"scanPushTaskSchedule_STRATEGY",
			    "IPList":
			    [
			        "127.0.0.1"
			    ],
			    "numOfSingleServer":0,
			    "assignNum":8,
			    "kind":"Schedule",
			    "taskName":"scanPushTaskSchedule_TASK",
			    "taskParameter":"",
			    "sts":"resume"
			}]


			//如上示例：
			测试数据1
			factoryList.size()=8
			scheduleStrategy.getAssignNum()=8
			scheduleStrategy.getNumOfSingleServer()=0
			那么 nums=[1, 1, 1, 1, 1, 1, 1, 1]
   			int[] nums = ScheduleUtil.assignTaskNumber(factoryList.size(), scheduleStrategy.getAssignNum(), scheduleStrategy.getNumOfSingleServer());

   			测试数据2
   			factoryList.size()=7
			scheduleStrategy.getAssignNum()=8
			scheduleStrategy.getNumOfSingleServer()=0
			那么 nums=[2, 1, 1, 1, 1, 1, 1]

            for(int i = 0; i < factoryList.size(); ++i) {
                ScheduleStrategyRunntime factory = (ScheduleStrategyRunntime)factoryList.get(i);
                this.scheduleStrategyManager.updateStrategyRunntimeReqestNum(run.getStrategyName(), factory.getUuid(), nums[i]);

				//数据格式：修改任务的其中一台机器的配置信息
				测试数据1，第1台机器， "requestNum":1,
				测试数据2，第1台机器， "requestNum":2,
				$rootPath/$strategy/$strategyName/$uuid
                [{
				    "strategyName":"scanPushTaskSchedule_STRATEGY",
				    "uuid":"10.187.82.71$host-10-187-82-71$8D5224B24A494D389216A3CAD4299B86$0000000457",
				    "requestNum":1,
				    "currentNum":0,
				    "message":""
				}]

            }

		}	            
=>
com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory.reRunScheduleServer();
	//查看当前机器参与执行的任务清单List<ScheduleStrategyRunntime> 
	Iterator i$ = this.scheduleStrategyManager.loadAllScheduleStrategyRunntimeByUUID(this.uuid).iterator();
		=>
		//第1层遍历，得到当前机器参与的所有的任务
		$rootpath/strategy/ children
		Iterator i$ = this.scheduleStrategyManager.loadAllScheduleStrategyRunntimeByUUID(this.uuid).iterator();

			//第2层遍历，得到当前机器会参与执行哪个任务
			if(this.getZooKeeper().exists(zkPath + "/" + taskType + "/" + managerFactoryUUID, false) != null) {
	                result.add(this.loadScheduleStrategyRunntime(taskType, managerFactoryUUID));
	        }
        	i$.next <ScheduleStrategyRunntime>元素格式      
        	[{
        	    "strategyName":"scanPushTaskSchedule_STRATEGY",
        	    "uuid":"10.187.58.131$host-10-187-58-131$4B408C4F614B47D29068D25889F924CA$0000000456",
        	    "requestNum":1,
        	    "currentNum":0,
        	    "message":""
        	}]

    ScheduleStrategyRunntime run = (ScheduleStrategyRunntime)i$.next();
    ScheduleStrategy strategy1 = this.scheduleStrategyManager.loadStrategy(run.getStrategyName());
        =>
        String zkPath = this.PATH_Strategy + "/" + strategyName;
        String valueString = new String(this.getZooKeeper().getData(zkPath, false, (Stat)null));
        ScheduleStrategy result = (ScheduleStrategy)this.gson.fromJson(valueString, ScheduleStrategy.class);
        //result数据格式<ScheduleStrategy>,返回的变量strategy1示例:
        [{
            "strategyName":"scanPushTaskSchedule_STRATEGY",
            "IPList":
            [
                "127.0.0.1"
            ],
            "numOfSingleServer":0,
            "assignNum":8,
            "kind":"Schedule",
            "taskName":"scanPushTaskSchedule_TASK",
            "taskParameter":"",
            "sts":"resume"
        }]

    IStrategyTask result = this.createStrategyTask(strategy1);
=>
com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory.createStrategyTask(ScheduleStrategy strategy);
    if(Kind.Schedule == strategy.getKind()) {
        //baseTaskType="scanPushTaskSchedule_TASK"
        //ownSign="BASE"
        String baseTaskType = ScheduleUtil.splitBaseTaskTypeFromTaskType(strategy.getTaskName());
        String ownSign = ScheduleUtil.splitOwnsignFromTaskType(strategy.getTaskName());
        result = new TBScheduleManagerStatic(this, baseTaskType, ownSign, this.scheduleDataManager);
    }
=>
com.taobao.pamirs.schedule.taskmanager.TBScheduleManagerStatic(this, baseTaskType, ownSign, this.scheduleDataManager);
    this.taskTypeInfo = this.scheduleCenter.loadTaskTypeBaseInfo(baseTaskType);
        //zkPath=$rootPaht/baseTaskType/scanPushTaskSchedule_TASK
        String zkPath = this.PATH_BaseTaskType + "/" + baseTaskType;
        //taskTypeInfo结构
        //valueString=[{
                "baseTaskType":"scanPushTaskSchedule_TASK",
                "heartBeatRate":5000,
                "judgeDeadInterval":60000,
                "sleepTimeNoData":0,
                "sleepTimeInterval":0,
                "fetchDataNumber":500,
                "executeNumber":1,
                "threadNumber":1,
                "processorType":"SLEEP",
                "permitRunStartTime":"0/10 * * * * ?",
                "expireOwnSignInterval":1.0,
                "dealBeanName":"scanPushTaskSchedule",
                "taskParameter":"",
                "taskKind":"static",
                "taskItems":
                [
                    "0",
                    "1",
                    "2",
                    "3",
                    "4",
                    "5",
                    "6",
                    "7"
                ],
                "maxTaskItemsOfOneThreadGroup":0,
                "version":0,
                "sts":"resume"
            }]
        String valueString = new String(this.getZooKeeper().getData(zkPath, false, (Stat)null));
        ScheduleTaskType result = (ScheduleTaskType)this.gson.fromJson(valueString, ScheduleTaskType.class);

    //baseTaskType="scanPushTaskSchedule_TASK"
    //aFactory.getIp()=10.1.128.53
    //this.taskTypeInfo.getExpireOwnSignInterval()=1.0

    this.scheduleCenter.clearExpireTaskTypeRunningInfo(baseTaskType, aFactory.getIp() + "清除过期OWN_SIGN信息", this.taskTypeInfo.getExpireOwnSignInterval());
        //查找$rootPath/baseTaskType/scanPushTaskSchedule_TASK 孩子节点
        Iterator i$ = this.getZooKeeper().getChildren(this.PATH_BaseTaskType + "/" + baseTaskType, false).iterator();
        while(i$.hasNext()) {
            //name="scanPushTaskSchedule_TASK"
            String name = (String)i$.next();

            //zkPath=$rootPath/baseTaskType/
                 /scanPushTaskSchedule_TASK/scanPushTaskSchedule_TASK/taskItem
            String zkPath = this.PATH_BaseTaskType + "/" + baseTaskType + "/" + name + "/" + this.PATH_TaskItem;

            Stat stat = this.getZooKeeper().exists(zkPath, false);

            //stat.getMtime()  这个节点在zk服务器上的最后修改时间
            //this.getSystemTime()  当前应用程序启动时会在zk服务器上创建一个临时节点，从而可以得到当前应用程序启动时对应的zk服务器时间 zkBaseTime 
            //当前应用程序启动时对应的当前服务器时间this.loclaBaseTime
            //getSystemTime()=this.zkBaseTime + (System.currentTimeMillis() - this.loclaBaseTime);
            //当this.getSystemTime() - stat.getMtime()大于expireDateInternal(目前expireDateInternal=1)天时，删除该节点
            if(stat == null || this.getSystemTime() - stat.getMtime() > (long)(expireDateInternal * 24.0D * 3600.0D * 1000.0D)) {
                ZKTools.deleteTree(this.getZooKeeper(), this.PATH_BaseTaskType + "/" + baseTaskType + "/" + name);
            }
        }

    //"dealBeanName":"scanPushTaskSchedule"
    Object dealBean = aFactory.getBean(this.taskTypeInfo.getDealBeanName()); 
    this.taskDealBean = (IScheduleTaskDeal)dealBean;

    //"judgeDeadInterval":60000
    //"heartBeatRate":5000
    if(this.taskTypeInfo.getJudgeDeadInterval() < this.taskTypeInfo.getHeartBeatRate() * 5L) {
        throw new Exception("数据配置存在问题，死亡的时间间隔，至少要大于心跳线程的5倍。当前配置数据：JudgeDeadInterval = " + this.taskTypeInfo.getJudgeDeadInterval() + ",HeartBeatRate = " + this.taskTypeInfo.getHeartBeatRate());
    } else {
        //currenScheduleServer其类型：ScheduleServer
        this.currenScheduleServer = ScheduleServer.createScheduleServer(this.scheduleCenter, baseTaskType, ownSign, this.taskTypeInfo.getThreadNumber(), this.factory.getIp());
            ScheduleServer result = new ScheduleServer();
            // result.baseTaskType="scanPushTaskSchedule_TASK"
            result.baseTaskType = aBaseTaskType;
            // ownSign="BASE"
            result.ownSign = aOwnSign;
            // result.taskType ="scanPushTaskSchedule_TASK"
            result.taskType = ScheduleUtil.getTaskTypeByBaseAndOwnSign(aBaseTaskType, aOwnSign);
            result.ip = ip;
            result.hostName = ScheduleUtil.getLocalHostName();
            //result.registerTime = zk服务器上的时间戳
            result.registerTime = new Timestamp(aScheduleCenter.getSystemTime());
            // result.threadNumber=1,
            result.threadNum = aThreadNum;
            result.heartBeatTime = null;
            result.dealInfoDesc = "调度初始化";
            result.version = 0L;
            result.uuid = result.ip + "$" + UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
            SimpleDateFormat DATA_FORMAT_yyyyMMdd = new SimpleDateFormat("yyMMdd");
            String s = DATA_FORMAT_yyyyMMdd.format(new Date(aScheduleCenter.getSystemTime()));
            result.id = Long.parseLong(s) * 100000000L + (long)Math.abs(result.uuid.hashCode() % 100000000);

        this.currenScheduleServer.setManagerFactoryUUID(this.factory.getUuid());
        this.scheduleCenter.registerScheduleServer(this.currenScheduleServer);
            //经过下面2步后，zkPath =$rootPath/baseTaskType/scanPushTaskSchedule_TASK/scanPushTaskSchedule_TASK/server

            String zkPath = this.PATH_BaseTaskType + "/" + server.getBaseTaskType() + "/" + server.getTaskType();
            zkPath = zkPath + "/" + this.PATH_Server;

            //zkServerPath=$rootPath/baseTaskType/scanPushTaskSchedule_TASK/scanPushTaskSchedule_TASK/server/scanPushTaskSchedule_TASK$10.187.58.131$2E02AB8AAE064E22890D9854F75DE03A$
            //值得注意的是这里的uuid是重新生成的，而不是直接取$rootPath/factory下的某个节点名字的
            String zkServerPath = zkPath + "/" + server.getTaskType() + "$" + server.getIp() + "$" + UUID.randomUUID().toString().replaceAll("-", "").toUpperCase() + "$";

            //realPath=//zkServerPath=$rootPath/baseTaskType/scanPushTaskSchedule_TASK/scanPushTaskSchedule_TASK/server/scanPushTaskSchedule_TASK$10.187.58.131$2E02AB8AAE064E22890D9854F75DE03A$0000000013
            realPath = this.getZooKeeper().create(zkServerPath, (byte[])null, this.zkManager.getAcl(), CreateMode.PERSISTENT_SEQUENTIAL);

            //uuid=scanPushTaskSchedule_TASK$10.187.58.131$2E02AB8AAE064E22890D9854F75DE03A$0000000013
            server.setUuid(realPath.substring(realPath.lastIndexOf("/") + 1));

            //heartBeatTime取的是zk服务器上时间戳
            Timestamp heartBeatTime = new Timestamp(this.getSystemTime());
            server.setHeartBeatTime(heartBeatTime);

            //给刚刚创建的节点realPath赋值
            String valueString = this.gson.toJson(server);
            this.getZooKeeper().setData(realPath, valueString.getBytes(), -1);
            server.setRegister(true);

            //server=valueString= zkpath(realPath).data
            [{
                "uuid":"scanPushTaskSchedule_TASK$10.187.58.131$2E02AB8AAE064E22890D9854F75DE03A$0000000013",
                "id":17120996565936,
                "taskType":"scanPushTaskSchedule_TASK",
                "baseTaskType":"scanPushTaskSchedule_TASK",
                "ownSign":"BASE",
                "ip":"10.187.58.131",
                "hostName":"host-10-187-58-131",
                "threadNum":1,
                "registerTime":"2017-12-09 11:38:59",
                "heartBeatTime":"2017-12-11 12:08:52",
                "lastFetchDataTime":"2017-12-11 12:08:44",
                "dealInfoDesc":"没有数据,
                暂停调度:FetchDataCount\u003d17212,
                FetchDataNum\u003d0,
                DealDataSucess\u003d0,
                DealDataFail\u003d0,
                DealSpendTime\u003d0,
                otherCompareCount\u003d0",
                "nextRunStartTime":"2017-12-11 12:09:00",
                "nextRunEndTime":"当不能获取到数据的时候pause",
                "version":69370,
                "isRegister":true,
                "managerFactoryUUID":"10.187.58.131$host-10-187-58-131$BAA79AF695AD4B0D85B61B7815982486$0000000472"
            }]
            //备注:\u003d代表符号“=”

        this.mBeanName = "pamirs:name=schedule.ServerMananger." + this.currenScheduleServer.getUuid();

        //创建Timer->heartBeatTimer
        this.heartBeatTimer = new Timer(this.currenScheduleServer.getTaskType() + "-" + this.currentSerialNumber + "-HeartBeat");

        //当前时间500ms之后启动HeartBeatTimerTask，以getHeartBeatRate()=5000s的频次重复执行
        this.heartBeatTimer.schedule(new HeartBeatTimerTask(this), new Date(System.currentTimeMillis() + 500L), this.taskTypeInfo.getHeartBeatRate());

        this.initial();
        ==>
com.taobao.pamirs.schedule.taskmanager.TBScheduleManagerStatic.initial()  

    //threadname=scanPushTaskSchedule_TASK+ "-" + 24 + "-StartProcess"
    new Thread(this.currenScheduleServer.getTaskType() + "-" + this.currentSerialNumber + "-StartProcess") {
        public void run() {
            TBScheduleManagerStatic.log.info("开始获取调度任务队列...... of " + TBScheduleManagerStatic.this.currenScheduleServer.getUuid());
            while(!TBScheduleManagerStatic.this.isRuntimeInfoInitial) {
                TBScheduleManagerStatic.this.initialRunningInfo();
                TBScheduleManagerStatic.this.isRuntimeInfoInitial = TBScheduleManagerStatic.this.scheduleCenter.isInitialRunningInfoSucuss(TBScheduleManagerStatic.this.currenScheduleServer.getBaseTaskType(), TBScheduleManagerStatic.this.currenScheduleServer.getOwnSign());
                    String taskType = ScheduleUtil.getTaskTypeByBaseAndOwnSign(baseTaskType, ownSign);
                    String leader = this.getLeader(this.loadScheduleServerNames(taskType));
                    String zkPath = this.PATH_BaseTaskType + "/" + baseTaskType + "/" + taskType + "/" + this.PATH_TaskItem;
                    if(this.getZooKeeper().exists(zkPath, false) != null) {
                        byte[] curContent = this.getZooKeeper().getData(zkPath, false, (Stat)null);
                        if(curContent != null && (new String(curContent)).equals(leader)) {
                            return true;
                        }
                    }
            }

            TBScheduleManagerStatic.log.info("获取到任务处理队列，开始调度：" + str + "  of  " + TBScheduleManagerStatic.this.currenScheduleServer.getUuid());

            
            TBScheduleManagerStatic.this.taskItemCount = TBScheduleManagerStatic.this.scheduleCenter.loadAllTaskItem(TBScheduleManagerStatic.this.currenScheduleServer.getTaskType()).size();

            TBScheduleManagerStatic.this.computerStart();
            ==>
//最最重要的方法，决定各个任务什么时候开始执行的            
com.taobao.pamirs.schedule.taskmanager.TBScheduleManagerStatic.computerStart()
    //this.taskTypeInfo.getPermitRunStartTime()="permitRunStartTime":"0/10 * * * * ?"
    String tmpStr = this.taskTypeInfo.getPermitRunStartTime();
    CronExpression cexpStart = new CronExpression(tmpStr);
    Date current = new Date(this.scheduleCenter.getSystemTime());

    //第1次执行的时间
    Date firstStartTime = cexpStart.getNextValidTimeAfter(current);
    this.heartBeatTimer.schedule(new PauseOrResumeScheduleTask(this, this.heartBeatTimer, PauseOrResumeScheduleTask.TYPE_RESUME, tmpStr), firstStartTime);

    //这个地方需要注意下，任务执行交给了Timer(heartBeatTimer)去定时触发（触发的时间点：firstStartTime），这个地方写的只是触发1次
    //PauseOrResumeScheduleTask.run()方法中会再次调用这个timer，等到下个触发周期再执行的

    ==>
com.taobao.pamirs.schedule.taskmanager.PauseOrResumeScheduleTask(TBScheduleManager aManager, Timer aTimer, int aType, String aCronTabExpress) 
    public void run() {
        if(this.type == TYPE_PAUSE) {
            this.manager.pause("到达终止时间,pause调度");
            this.manager.getScheduleServer().setNextRunEndTime(ScheduleUtil.transferDataToString(nextTime));
        } else {
            this.manager.resume("到达开始时间,resume调度");
            this.manager.getScheduleServer().setNextRunStartTime(ScheduleUtil.transferDataToString(nextTime));
        }
    }
==>
com.taobao.pamirs.schedule.taskmanager.TBScheduleManager.resume("到达开始时间,resume调度");
    this.processor = new TBScheduleProcessorSleep(this, this.taskDealBean, this.statisticsInfo);
    this.taskTypeInfo.setProcessorType("SLEEP");
    this.rewriteScheduleInfo();
==>
com.taobao.pamirs.schedule.taskmanager.TBScheduleProcessorSleep(this, this.taskDealBean, this.statisticsInfo);
    for(int i = 0; i < this.taskTypeInfo.getThreadNumber(); ++i) {
        this.startThread(i);
    }
==>
com.taobao.pamirs.schedule.taskmanager.TBScheduleProcessorSleep.startThread(i);
    Thread thread = new Thread(this);
    this.threadList.add(thread);
    // threadName = scanPushTaskSchedule_TASK-24-exe0
    String threadName = this.scheduleManager.getScheduleServer().getTaskType() + "-" + this.scheduleManager.getCurrentSerialNumber() + "-exe" + index;
    thread.setName(threadName);
    thread.start();
==>
com.taobao.pamirs.schedule.taskmanager.TBScheduleProcessorSleep.run()
    int size1 = this.loadScheduleData();
        List tmpList = this.taskDealBean.selectTasks(...)
        this.taskList.addAll(tmpList);
    ((IScheduleTaskDealSingle)this.taskDealBean).execute(executeTask, this.scheduleManager.getScheduleServer().getOwnSign())

==>
com.taobao.pamirs.schedule.taskmanager.TBScheduleManager.rewriteScheduleInfo()
    if(!this.scheduleCenter.refreshScheduleServer(this.currenScheduleServer)) {
            this.clearMemoInfo();
            this.scheduleCenter.registerScheduleServer(this.currenScheduleServer);
        }
==>
com.taobao.pamirs.schedule.zk.ScheduleDataManager4ZK.registerScheduleServer(this.currenScheduleServer);
    Timestamp heartBeatTime = new Timestamp(this.getSystemTime());
    String zkPath = this.PATH_BaseTaskType + "/" + server.getBaseTaskType() + "/" + server.getTaskType() + "/" + this.PATH_Server + "/" + server.getUuid();
    if(this.getZooKeeper().exists(zkPath, false) == null) {
        server.setRegister(false);
        return false;
    } else {
        Timestamp oldHeartBeatTime = server.getHeartBeatTime();
        server.setHeartBeatTime(heartBeatTime);
        server.setVersion(server.getVersion() + 1L);
        String valueString = this.gson.toJson(server);

        try {
            this.getZooKeeper().setData(zkPath, valueString.getBytes(), -1);
            return true;
        } catch (Exception var7) {
            server.setHeartBeatTime(oldHeartBeatTime);
            server.setVersion(server.getVersion() - 1L);
            throw var7;
        }
    }

































	


