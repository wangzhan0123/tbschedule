
3.3.4升级作了哪些调整
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
如果10:00:00秒执行了1次，用了12秒，那么这个期间，10:00:10,10:00:20,10:00:30任务会触发会执行么,结论：10:00:10不会执行，10:00:20会会执行，10:00:30不会执行，
解释说明：TBScheduleManager.resume()方法还是会被每10秒的频率触发，但是真实的任务不会执行，原因在于这个变量 TBScheduleManager.isPauseSchedule
真实的任务在执行时会将TBScheduleManager.isPauseSchedule=false,而TBScheduleManager.resume()每次在调用执行任务时会校验这个变量只有当TBScheduleManager.isPauseSchedule=true时才会执行

TBScheduleManager.isPauseSchedule=true的条件，达到了结束任务触发的规则permitRunEndTime
permitRunStartTime有值，则以实际值进行暂停
permitRunStartTime没有值，则当本次任务selectTasks()返回条数==0时进行暂停
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


<td>任务参数:</td>
<td><input type="text" id="taskParameter" name="taskParameter" value="<%=scheduleStrategy.getTaskParameter()%>" width="30"></td>
<td>逗号分隔的Key-Value。 对任务类型为Schedule的无效，需要通过任务管理来配置的</td>

NotSleep和Sleep模式
NotSleep模式就是一个线程组内的所有线程都可以执行selectTasks方法，但是数据要存储起来，以防止其他线程处理重复，需要重写getComparator方法
Sleep模式就是一个线程组内的所有线程中只能有一个线程去执行selectTasks方法，然后所有线程去执行execute方法，不需要重写getComparator方法

问题五.任务配置修改了，没反应？
策略修改会立即生效，任务配置不会生效
Tbschedule对策略的修改是及时感知的，但是对于任务项的修改则不是，一旦调度分配好任务项开始执行的时候，手动去修改任务项的执行时间，
增加任务项等等都是没有反应的，除非你手工的去重启调度策略或者重新启动你的服务器。当然也有特殊情况，那就是zookeeper连接超时时导致
的任务调度重新分配（当然这种情况也不是我们希望的）。

问题4.线程组数不一致
有时候观察任务项，会发现线程组数超过的配置项，而且程序中也会发现抛出“自启动以来，超过10个心跳周期，还 没有获取到分配的任务队列”
出现上述问题有很多原因，常见的是服务器重启引起导致zookeeper断开连接，启动之后，zookeeper会重新调度，也就是会重新创造
出新的线程组，不过一般不用担心，tbschedule会删除无用的节点，只需要等待几秒就好

问题一，任务项没配置
任务项至少需要1项
对于许多刚接触这块的同学来说，第一点就是不明白任务项的必要性，没有任务项，线程组分配不到任务，也就不会进入selectTasks方法，
并且启动worker服务会抛出异常



触发规则配置
开始时间 不配置
结束时间 不配置
启动后立即执行，每次都是先休眠leepTimeInterval再执行selectTasks方法，会一直按照这个步骤循环下去，selectTask方法仅执行多次

开始时间 不配置
结束时间 配置
启动后立即执行，每次都是先休眠leepTimeInterval再执行selectTasks方法，会一直按照这个步骤循环下去，selectTask方法仅执行多次

开始时间 配置
结束时间 不配置
在开始时间进行执行，当selectTask方法 return null或者 return new ArrayList()时暂停任务，selectTask方法仅执行1次

开始时间 配置
结束时间 配置
在开始时间进行执行，每次都是先休眠leepTimeInterval再执行selectTasks方法，会一直按照这个步骤循环下去，直到结束时间到达后暂停






