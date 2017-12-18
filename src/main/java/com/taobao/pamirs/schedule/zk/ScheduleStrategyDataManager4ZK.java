package com.taobao.pamirs.schedule.zk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.taobao.pamirs.schedule.strategy.ManagerFactoryInfo;
import com.taobao.pamirs.schedule.strategy.ScheduleStrategy;
import com.taobao.pamirs.schedule.strategy.ScheduleStrategyRunntime;
import com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Writer;
import java.sql.Timestamp;
import java.util.*;

public class ScheduleStrategyDataManager4ZK {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleStrategyDataManager4ZK.class);

    private ZKManager zkManager;
    private String PATH_Strategy;
    private String PATH_Factory;
    private Gson gson;

    //在Spring对象创建完毕后，创建内部对象
    public ScheduleStrategyDataManager4ZK(ZKManager aZkManager) throws Exception {
        this.zkManager = aZkManager;
        gson = new GsonBuilder().registerTypeAdapter(Timestamp.class, new TimestampTypeAdapter()).setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        this.PATH_Strategy = this.zkManager.getRootPath() + "/strategy";
        this.PATH_Factory = this.zkManager.getRootPath() + "/factory";

        if (this.getZooKeeper().exists(this.PATH_Strategy, false) == null) {
            ZKTools.createPath(getZooKeeper(), this.PATH_Strategy, CreateMode.PERSISTENT, this.zkManager.getAcl());
        }
        if (this.getZooKeeper().exists(this.PATH_Factory, false) == null) {
            ZKTools.createPath(getZooKeeper(), this.PATH_Factory, CreateMode.PERSISTENT, this.zkManager.getAcl());
        }
    }

    /**
     * 获得指定任务的概要配置信息,数据格式示例：
     * {
     *     "strategyName":"scanPushTaskSchedule_STRATEGY",
     *     "IPList":
     *     [
     *         "127.0.0.1"
     *     ],
     *     "numOfSingleServer":0,
     *     "assignNum":8,
     *     "kind":"Schedule",
     *     "taskName":"scanPushTaskSchedule_TASK",
     *     "taskParameter":"",
     *     "sts":"resume"
     * }
     *
     * @param strategyName
     * @return
     * @throws Exception
     */
    public ScheduleStrategy loadStrategy(String strategyName) throws Exception {
        String zkPath = this.PATH_Strategy + "/" + strategyName;
        if (this.getZooKeeper().exists(zkPath, false) == null) {
            return null;
        }
        String valueString = new String(this.getZooKeeper().getData(zkPath, false, null));
        ScheduleStrategy result = (ScheduleStrategy) this.gson.fromJson(valueString, ScheduleStrategy.class);
        return result;
    }

    public void createScheduleStrategy(ScheduleStrategy scheduleStrategy) throws Exception {
        String zkPath = this.PATH_Strategy + "/" + scheduleStrategy.getStrategyName();
        String valueString = this.gson.toJson(scheduleStrategy);
        if (this.getZooKeeper().exists(zkPath, false) == null) {
            this.getZooKeeper().create(zkPath, valueString.getBytes(), this.zkManager.getAcl(), CreateMode.PERSISTENT);
        } else {
            throw new Exception("调度策略" + scheduleStrategy.getStrategyName() + "已经存在,如果确认需要重建，请先调用deleteMachineStrategy(String taskType)删除");
        }
    }

    public void updateScheduleStrategy(ScheduleStrategy scheduleStrategy)
            throws Exception {
        String zkPath = this.PATH_Strategy + "/" + scheduleStrategy.getStrategyName();
        String valueString = this.gson.toJson(scheduleStrategy);
        if (this.getZooKeeper().exists(zkPath, false) == null) {
            this.getZooKeeper().create(zkPath, valueString.getBytes(), this.zkManager.getAcl(), CreateMode.PERSISTENT);
        } else {
            this.getZooKeeper().setData(zkPath, valueString.getBytes(), -1);
        }

    }

    public void deleteMachineStrategy(String taskType) throws Exception {
        deleteMachineStrategy(taskType, false);
    }

    public void pause(String strategyName) throws Exception {
        ScheduleStrategy strategy = this.loadStrategy(strategyName);
        strategy.setSts(ScheduleStrategy.STS_PAUSE);
        this.updateScheduleStrategy(strategy);
    }

    public void resume(String strategyName) throws Exception {
        ScheduleStrategy strategy = this.loadStrategy(strategyName);
        strategy.setSts(ScheduleStrategy.STS_RESUME);
        this.updateScheduleStrategy(strategy);
    }

    public void deleteMachineStrategy(String taskType, boolean isForce) throws Exception {
        String zkPath = this.PATH_Strategy + "/" + taskType;
        if (isForce == false && this.getZooKeeper().getChildren(zkPath, null).size() > 0) {
            throw new Exception("不能删除" + taskType + "的运行策略，会导致必须重启整个应用才能停止失去控制的调度进程。" +
                    "可以先清空IP地址，等所有的调度器都停止后再删除调度策略");
        }
        ZKTools.deleteTree(this.getZooKeeper(), zkPath);
    }

    /**
     * 获取所有的任务概要配置
     * List<$rootPath/$strategy/children> 并按照任务名称来排序
     * @return
     * @throws Exception
     */
    public List<ScheduleStrategy> loadAllScheduleStrategy() throws Exception {
        String zkPath = this.PATH_Strategy;
        List<ScheduleStrategy> result = new ArrayList<ScheduleStrategy>();
        //这一步只能获取各个节点的名称，节点的data数据（最终封装成ScheduleStrategy）通过下面的方法loadStrategy来获取
        List<String> names = this.getZooKeeper().getChildren(zkPath, false);
        Collections.sort(names);
        for (String name : names) {
            result.add(this.loadStrategy(name));
        }
        return result;
    }

    /**
     * 注册ManagerFactory
     *
     * <p>1.当前机器会在zk服务器添加临时自增节点 $rootPath/factory/$uuid CreateMode.EPHEMERAL_SEQUENTIAL </p>
     * <p>2.1当前机器如果满足IPList规则，则会在zk服务器添加临时自增节点 $rootPath/strategy/$uuid CreateMode.EPHEMERAL_SEQUENTIAL  </p>
     * <p>2.2当前机器如果不满足IPList规则，则会在zk服务器上进行删除，并记录到返回值List集合里 </p>
     *
     * <p>当前应用所有的服务器X1...Xn都会创建一个临时自增节点 $rootPath/$factory/$uuid CreateMode.EPHEMERAL_SEQUENTIAL </p>
     * <p>基于任务的概要配置参数IPList，比如["127.0.0.1"]或者["192.168.195.101，192.168.195.202"],判断服务器X1...Xn当中具体会有哪几个参与到这个任务当中  </p>
     * @param managerFactory
     * @return 需要全部注销的任务名称集合，例如不满足IPList规则的
     * @throws Exception
     */
    public List<String> registerManagerFactory(TBScheduleManagerFactory managerFactory) throws Exception {
        if (managerFactory.getUuid() == null) {
            String uuid = managerFactory.getIp() + "$" + managerFactory.getHostName() + "$" + UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
            String zkPath = this.PATH_Factory + "/" + uuid + "$";
            zkPath = this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.EPHEMERAL_SEQUENTIAL);
            //下面这一步不能忽略，最终managerFactory赋值后的uuid，是在zk服务器上创建的临时节点名称，示例：uuid="10.187.133.52$host-10-187-133-52$786D516F7FA84FB6BF501B9B4FFF073D$0000000477"
            managerFactory.setUuid(zkPath.substring(zkPath.lastIndexOf("/") + 1));
        } else {
            //代码块能走到这里，表明uuid！=null,也就是表明uuid已经包含了自增序列了，所以接下来再次检测如果不存在时进行创建使用的是CreateMode.EPHEMERAL模式
            String zkPath = this.PATH_Factory + "/" + managerFactory.getUuid();
            if (this.getZooKeeper().exists(zkPath, false) == null) {
                zkPath = this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.EPHEMERAL);
            }
        }

        List<ScheduleStrategy> strategyList = loadAllScheduleStrategy();

        //记录下当前机器不应该参与处理的哪些任务List
        List<String> result = new ArrayList<String>();
        for (ScheduleStrategy scheduleStrategy : strategyList) {
            boolean isFind = false;
            //  任务状态sts=resume且IPList不为空
            if (ScheduleStrategy.STS_PAUSE.equalsIgnoreCase(scheduleStrategy.getSts()) == false && scheduleStrategy.getIPList() != null) {
                for (String ip : scheduleStrategy.getIPList()) {
                    if (ip.equals("127.0.0.1") || ip.equalsIgnoreCase("localhost") || ip.equals(managerFactory.getIp()) || ip.equalsIgnoreCase(managerFactory.getHostName())) {
                        //添加可管理TaskType
                        String zkPath = this.PATH_Strategy + "/" + scheduleStrategy.getStrategyName() + "/" + managerFactory.getUuid();
                        if (this.getZooKeeper().exists(zkPath, false) == null) {
                            zkPath = this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.EPHEMERAL);
                        }
                        isFind = true;
                        break;
                    }
                }
            }
            if (isFind == false) {
                // 把不满足IPList规则的从zk服务器上删除相应的节点，并记录到返回值List集合里
                String zkPath = this.PATH_Strategy + "/" + scheduleStrategy.getStrategyName() + "/" + managerFactory.getUuid();
                if (this.getZooKeeper().exists(zkPath, false) != null) {
                    logger.info("删除不满足IPList规则的zk服务器，zkPath="+zkPath);
                    ZKTools.deleteTree(this.getZooKeeper(), zkPath);
                    result.add(scheduleStrategy.getStrategyName());
                }
            }
        }
        return result;
    }

    /**
     * 注销服务，停止调度
     * @param managerFactory
     * @return
     * @throws Exception
     */
    public void unRregisterManagerFactory(TBScheduleManagerFactory managerFactory) throws Exception {
        for (String taskName : this.getZooKeeper().getChildren(this.PATH_Strategy, false)) {
            String zkPath = this.PATH_Strategy + "/" + taskName + "/" + managerFactory.getUuid();
            if (this.getZooKeeper().exists(zkPath, false) != null) {
                ZKTools.deleteTree(this.getZooKeeper(), zkPath);
            }
        }
    }

    /**
     * 查看指定strategyName下的uuid机器的data数据，并封装成ScheduleStrategyRunntime类型
     *
     * @param strategyName
     * @param uuid
     * @return
     * @throws Exception
     */
    public ScheduleStrategyRunntime loadScheduleStrategyRunntime(String strategyName, String uuid) throws Exception {
        String zkPath = this.PATH_Strategy + "/" + strategyName + "/" + uuid;
        ScheduleStrategyRunntime result = null;
        if (this.getZooKeeper().exists(zkPath, false) != null) {
            byte[] value = this.getZooKeeper().getData(zkPath, false, null);
            if (value != null) {
                String valueString = new String(value);
                result = (ScheduleStrategyRunntime) this.gson.fromJson(valueString, ScheduleStrategyRunntime.class);
                if (null == result) {
                    throw new Exception("gson 反序列化异常,对象为null");
                }
                if (null == result.getStrategyName()) {
                    throw new Exception("gson 反序列化异常,策略名字为null");
                }
                if (null == result.getUuid()) {
                    throw new Exception("gson 反序列化异常,uuid为null");
                }
            } else {
                //这个节点如果是这次创建的，getData就会是null的
                result = new ScheduleStrategyRunntime();
                result.setStrategyName(strategyName);
                result.setUuid(uuid);
                result.setRequestNum(0);
                result.setMessage("init");
            }
        }
        return result;
    }

    /**
     * 装载所有的策略运行状态
     * @return
     * @throws Exception
     */
    public List<ScheduleStrategyRunntime> loadAllScheduleStrategyRunntime() throws Exception {
        List<ScheduleStrategyRunntime> result = new ArrayList<ScheduleStrategyRunntime>();
        String zkPath = this.PATH_Strategy;
        for (String taskType : this.getZooKeeper().getChildren(zkPath, false)) {
            for (String uuid : this.getZooKeeper().getChildren(zkPath + "/" + taskType, false)) {
                result.add(loadScheduleStrategyRunntime(taskType, uuid));
            }
        }
        return result;
    }

    /**
     * 获得这个uuid所在机器能够参与的所有任务列表
     *
     * @param uuid
     * @return
     * @throws Exception
     */
    public List<ScheduleStrategyRunntime> loadAllScheduleStrategyRunntimeByUUID(String uuid) throws Exception {
        List<ScheduleStrategyRunntime> result = new ArrayList<ScheduleStrategyRunntime>();

        String zkPath = this.PATH_Strategy;
        List<String> strategyNameList = this.getZooKeeper().getChildren(zkPath, false);
        Collections.sort(strategyNameList);

        for (String strategyName : strategyNameList) {
            if (this.getZooKeeper().exists(zkPath + "/" + strategyName + "/" + uuid, false) != null) {
                ScheduleStrategyRunntime scheduleStrategyRunntime = loadScheduleStrategyRunntime(strategyName, uuid);
                result.add(scheduleStrategyRunntime);
            }
        }
        return result;
    }

    /**
     * 获得指定strategyName下的所有机器列表的data数据，并且是排过序的，排序规则：按照uuid中的自增序列号进行升序排列
     *
     * @param strategyName
     * @return
     * @throws Exception
     */
    public List<ScheduleStrategyRunntime> loadAllScheduleStrategyRunntimeByTaskType(String strategyName) throws Exception {
        List<ScheduleStrategyRunntime> result = new ArrayList<ScheduleStrategyRunntime>();
        String zkPath = this.PATH_Strategy;
        if (this.getZooKeeper().exists(zkPath + "/" + strategyName, false) == null) {
            return result;
        }
        List<String> uuidList = this.getZooKeeper().getChildren(zkPath + "/" + strategyName, false);
        //排序
        Collections.sort(uuidList, new Comparator<String>() {
            public int compare(String uuid1, String uuid2) {
                return uuid1.substring(uuid1.lastIndexOf("$") + 1).compareTo(uuid2.substring(uuid2.lastIndexOf("$") + 1));
            }
        });

        for (String uuid : uuidList) {
            result.add(loadScheduleStrategyRunntime(strategyName, uuid));
        }
        return result;
    }


    /**
     * 指定strategyName任务下的uuid机器参与执行的任务项数量为taskItemNum
     *
     * @param strategyName
     * @param uuid
     * @param taskItemNum
     * @throws Exception
     */
    public void updateStrategyRunntimeReqestNum(String strategyName, String uuid, int taskItemNum) throws Exception {
        //ScheduleStrategyRunntime result = null;
        //if (this.getZooKeeper().exists(zkPath, false) != null) {
        //    result = this.loadScheduleStrategyRunntime(strategyName, uuid);
        //} else {
        //    result = new ScheduleStrategyRunntime();
        //    result.setStrategyName(strategyName);
        //    result.setUuid(uuid);
        //    result.setRequestNum(taskItemNum);
        //    result.setMessage("");
        //}

        ScheduleStrategyRunntime result = this.loadScheduleStrategyRunntime(strategyName, uuid);
        if (result == null) {
            result = new ScheduleStrategyRunntime();
            result.setStrategyName(strategyName);
            result.setUuid(uuid);
            result.setRequestNum(taskItemNum);
            result.setMessage("");
        }

        result.setRequestNum(taskItemNum);
        String valueString = this.gson.toJson(result);

        String zkPath = this.PATH_Strategy + "/" + strategyName + "/" + uuid;
        this.getZooKeeper().setData(zkPath, valueString.getBytes(), -1);
    }

    /**
     * 更新调度过程中的信息
     * @param strategyName
     * @param manangerFactoryUUID
     * @param message
     * @throws Exception
     */
    public void updateStrategyRunntimeErrorMessage(String strategyName, String manangerFactoryUUID, String message) throws Exception {
        String zkPath = this.PATH_Strategy + "/" + strategyName + "/" + manangerFactoryUUID;
        ScheduleStrategyRunntime result = null;
        if (this.getZooKeeper().exists(zkPath, false) != null) {
            result = this.loadScheduleStrategyRunntime(strategyName, manangerFactoryUUID);
        } else {
            result = new ScheduleStrategyRunntime();
            result.setStrategyName(strategyName);
            result.setUuid(manangerFactoryUUID);
            result.setRequestNum(0);
        }
        result.setMessage(message);
        String valueString = this.gson.toJson(result);
        this.getZooKeeper().setData(zkPath, valueString.getBytes(), -1);
    }

    public void updateManagerFactoryInfo(String uuid, boolean isStart) throws Exception {
        String zkPath = this.PATH_Factory + "/" + uuid;
        if (this.getZooKeeper().exists(zkPath, false) == null) {
            throw new Exception("任务管理器不存在:" + uuid);
        }
        this.getZooKeeper().setData(zkPath, Boolean.toString(isStart).getBytes(), -1);
    }

    /**
     * 获取当前机器节点 /$rootPath/factory/$uuid data数据并组装成ManagerFactoryInfo类型
     *
     * @param uuid
     * @return
     * @throws Exception
     */
    public ManagerFactoryInfo loadManagerFactoryInfo(String uuid) throws Exception {
        String zkPath = this.PATH_Factory + "/" + uuid;
        if (this.getZooKeeper().exists(zkPath, false) == null) {
            throw new Exception("任务管理器不存在:" + uuid);
        }
        byte[] value = this.getZooKeeper().getData(zkPath, false, null);
        ManagerFactoryInfo result = new ManagerFactoryInfo();
        result.setUuid(uuid);
        if (value == null) {
            result.setStart(true);
        } else {
            result.setStart(Boolean.parseBoolean(new String(value)));
        }
        return result;
    }

    /**
     * 导入配置信息【目前支持baseTaskType和strategy数据】
     *
     * @param config
     * @param writer
     * @param isUpdate
     * @throws Exception
     */
    public void importConfig(String config, Writer writer, boolean isUpdate)
            throws Exception {
        ConfigNode configNode = gson.fromJson(config, ConfigNode.class);
        if (configNode != null) {
            String path = configNode.getRootPath() + "/"
                    + configNode.getConfigType();
            ZKTools.createPath(getZooKeeper(), path, CreateMode.PERSISTENT, zkManager.getAcl());
            String y_node = path + "/" + configNode.getName();
            if (getZooKeeper().exists(y_node, false) == null) {
                writer.append("<font color=\"red\">成功导入新配置信息\n</font>");
                getZooKeeper().create(y_node, configNode.getValue().getBytes(),
                        zkManager.getAcl(), CreateMode.PERSISTENT);
            } else if (isUpdate) {
                writer.append("<font color=\"red\">该配置信息已经存在，并且强制更新了\n</font>");
                getZooKeeper().setData(y_node,
                        configNode.getValue().getBytes(), -1);
            } else {
                writer.append("<font color=\"red\">该配置信息已经存在，如果需要更新，请配置强制更新\n</font>");
            }
        }
        writer.append(configNode.toString());
    }

    /**
     * 输出配置信息【目前备份baseTaskType和strategy数据】
     *
     * @param rootPath
     * @param writer
     * @throws Exception
     */
    public StringBuffer exportConfig(String rootPath, Writer writer)
            throws Exception {
        StringBuffer buffer = new StringBuffer();
        for (String type : new String[]{"baseTaskType", "strategy"}) {
            if (type.equals("baseTaskType")) {
                writer.write("<h2>基本任务配置列表：</h2>\n");
            } else {
                writer.write("<h2>基本策略配置列表：</h2>\n");
            }
            String bTTypePath = rootPath + "/" + type;
            List<String> fNodeList = getZooKeeper().getChildren(bTTypePath,
                    false);
            for (int i = 0; i < fNodeList.size(); i++) {
                String fNode = fNodeList.get(i);
                ConfigNode configNode = new ConfigNode(rootPath, type, fNode);
                configNode.setValue(new String(this.getZooKeeper().getData(bTTypePath + "/" + fNode, false, null)));
                buffer.append(gson.toJson(configNode));
                buffer.append("\n");
                writer.write(configNode.toString());
            }
            writer.write("\n\n");
        }
        if (buffer.length() > 0) {
            String str = buffer.toString();
            return new StringBuffer(str.substring(0, str.length() - 1));
        }
        return buffer;
    }

    public List<ManagerFactoryInfo> loadAllManagerFactoryInfo() throws Exception {
        String zkPath = this.PATH_Factory;
        List<ManagerFactoryInfo> result = new ArrayList<ManagerFactoryInfo>();
        List<String> names = this.getZooKeeper().getChildren(zkPath, false);
        Collections.sort(names, new Comparator<String>() {
            public int compare(String u1, String u2) {
                return u1.substring(u1.lastIndexOf("$") + 1).compareTo(
                        u2.substring(u2.lastIndexOf("$") + 1));
            }
        });
        for (String name : names) {
            ManagerFactoryInfo info = new ManagerFactoryInfo();
            info.setUuid(name);
            byte[] value = this.getZooKeeper().getData(zkPath + "/" + name, false, null);
            if (value == null) {
                info.setStart(true);
            } else {
                info.setStart(Boolean.parseBoolean(new String(value)));
            }
            result.add(info);
        }
        return result;
    }

    public void printTree(String path, Writer writer, String lineSplitChar)
            throws Exception {
        ZKTools.printTree(this.getZooKeeper(), path, writer, lineSplitChar);
    }

    public void deleteTree(String path) throws Exception {
        ZKTools.deleteTree(this.getZooKeeper(), path);
    }

    public ZooKeeper getZooKeeper() throws Exception {
        return this.zkManager.getZooKeeper();
    }

    public String getRootPath() {
        return this.zkManager.getRootPath();
    }
}
