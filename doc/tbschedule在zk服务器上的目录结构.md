各节点的状态信息
各节点的数据
zk服务器间集群机制，以及节点数据变化如何通知到其它节点
应用服务器间集群机制

-----------------------------------------------------------------------------------------------------------------
tbschedlue在zk服务器上的目录结构说明

$rootpath
根目录( CreateMode.PERSISTENT )
    appname1
    应用名称( CreateMode.PERSISTENT )
        strategy
        任务概要配置目录( CreateMode.PERSISTENT )
            scanPushTaskSchedule_STRATEGY
            任务名称1( CreateMode.PERSISTENT )
                10.190.18.240$host-10-190-18-240$323E9C8F1CDB48D58548A2F2FB7E2145$0000000463
                参与处理这个任务的服务器信息( CreateMode.EPHEMERAL_SEQUENTIAL )
                10.190.17.166$host-10-190-17-166$788EC68CA9C04298A78DF82A3B3B7222$0000000458
                参与处理这个任务的服务器信息( CreateMode.EPHEMERAL_SEQUENTIAL )
            scanWechatActivityMsgSchedule_STRATEGY
            任务名称2( CreateMode.PERSISTENT )
                ……
            ……
        baseTaskType
        任务详情配置目录( CreateMode.PERSISTENT )
             scanApprovalStatusSchedule_TASK
             任务名称1( CreateMode.PERSISTENT )
                scanApprovalStatusSchedule_TASK
                任务名称1( CreateMode.PERSISTENT ),这一级目录与上一级目录通常情况情况下是一样的,也可以是scanApprovalStatusSchedule_TASK$test
                    taskItem
                    任务项目录( CreateMode.PERSISTENT )
                        0 ( CreateMode.PERSISTENT )
                        1 ( CreateMode.PERSISTENT )
                        2 ( CreateMode.PERSISTENT )
                        3 ( CreateMode.PERSISTENT )
                        ……
                    server
                    线程组目录( CreateMode.PERSISTENT )
                        scanPushTaskSchedule_TASK$10.191.33.64$7D59B7AFA95C40FABCD78C0D6C0C2672$0000000035
                        具体线程组1( CreateMode.EPHEMERAL_SEQUENTIAL )
                        scanPushTaskSchedule_TASK$10.187.58.131$668116D0699F44DEB008B32FDAB25469$0000000037
                        具体线程2( CreateMode.EPHEMERAL_SEQUENTIAL )
            ……
        factory
        这个应用的全部服务器目录( CreateMode.PERSISTENT )
            10.190.18.240$host-10-190-18-240$323E9C8F1CDB48D58548A2F2FB7E2145$0000000463
            服务器1( CreateMode.EPHEMERAL_SEQUENTIAL )
            10.187.82.71$host-10-187-82-71$BFFAD85238BF48F1AA443F2FB4AF4CBD$0000000474
            服务器2( CreateMode.EPHEMERAL_SEQUENTIAL )
            ……
    appname2 --应用名称
        ……
    ……
-----------------------------------------------------------------------------------------------------------------
下面分别来讲讲上面提到的3个最重要的目录
-----------------------------------------------------------------------------------------------------------------
-----------------------------------------------------------------------------------------------------------------
strategy
    scanPushTaskSchedule_STRATEGY ->【taskName-任务名称,IPList-允许参与处理任务的服务器,assignNum-线程组数量,numOfSingleServer-单JVM允许最大线程组数量,
           taskParameter-任务参数，sts-(值可为resume,pause)】
        10.190.18.240$host-10-190-18-240$323E9C8F1CDB48D58548A2F2FB7E2145$0000000463 ->【strategyName-任务名称,uuid-机器编号,requestNum-,currentNum-,message- 】
        10.190.17.166$host-10-190-17-166$788EC68CA9C04298A78DF82A3B3B7222$0000000458
        ……
    scanWechatActivityMsgSchedule_STRATEGY
        ……
    ……

baseTaskType
    scanApprovalStatusSchedule_TASK ->【dealBeanName-spingbean名称，heartBeatRate-心跳频率(s)，judgeDeadInterval-死亡时间间隔，sleepTimeNoData-单次执行完selectTasks方法返回null后休眠时间(s)，
                                      sleepTimeInterval，fetchDataNumber-单次获取数据条数，executeNumber，threadNumber-单个线程组线程数量
                                      ，processorType-(值可为SLEEP) ，permitRunStartTime-触发规则，expireOwnSignInterval，
                                      taskParameter，taskKind-(值可为static)，taskItems-任务项，maxTaskItemsOfOneThreadGroup-单个线程组最大任务项】
        scanApprovalStatusSchedule_TASK -> null
            taskItem ->【scanPushTaskSchedule_TASK$10.190.18.240$E32B5704BBDC4FA38E10E70ECE847499$0000000000】
            server ->【reload=true】

factory -- 这个应用具体的机器清单，最后1个$后面的数字进行比较，最小的会设置为Leader
    10.190.18.240$host-10-190-18-240$323E9C8F1CDB48D58548A2F2FB7E2145$0000000463  ( CreateMode.EPHEMERAL_SEQUENTIAL )
    10.187.82.71$host-10-187-82-71$BFFAD85238BF48F1AA443F2FB4AF4CBD$0000000474
    ……
--------------------------------------------------------------------------------
taskItem ->【scanPushTaskSchedule_TASK$10.190.18.240$E32B5704BBDC4FA38E10E70ECE847499$0000000000】
            taskItem节点的值记录了当前任务参与执行的服务器Leader信息
    0 -> 【null】
        deal_desc
        parameter
        req_server
        cur_server ->【scanPushTaskSchedule_TASK$10.190.18.240$E32B5704BBDC4FA38E10E70ECE847499$0000000000】
        sts ->【ACTIVTE】
    1
    2
    3
    4
    5
    6
    7



