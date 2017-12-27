package com.taobao.pamirs.schedule.taskmanager;

import com.taobao.pamirs.schedule.ScheduleUtil;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 调度服务器信息定义
 * @author xuannan
 *
 */
public class ScheduleServer {
    /**  全局唯一编号  */
    private String uuid;
    private long id;
    private String managerFactoryUUID;

    /**  原始任务类型  */
    //TODO:建议更名为taskName
    private String baseTaskType;

    /** 任务类型,示例：taskName$test */
    private String taskType;

    private String ownSign;
    /**  机器IP地址  */
    private String ip;

    /** 机器名称  */
    private String hostName;

    /**  数据处理线程数量  */
    private int threadNum;
    /** 服务开始时间  */
    private Timestamp registerTime;
    /** 最后一次心跳通知时间 */
    private Timestamp heartBeatTime;
    /**  最后一次取数据时间  */
    private Timestamp lastFetchDataTime;

    private String nextRunStartTime;

    private String nextRunEndTime;
    /** zk服务器的当前时间 */
    private Timestamp centerServerTime;

    /** 数据版本号 */
    private long version;

    private boolean isRegister;
    /**
     * 处理描述信息，例如读取的任务数量，处理成功的任务数量，处理失败的数量，处理耗时
     * FetchDataCount=4430,FetcheDataNum=438570,DealDataSucess=438570,DealDataFail=0,DealSpendTime=651066
     */
    private String dealInfoDesc;

    public ScheduleServer() {

    }

    public static ScheduleServer createScheduleServer(IScheduleDataManager scheduleTaskManager, String baseTaskType, String ownSign, int threadNum) throws Exception {
        ScheduleServer result = new ScheduleServer();
        result.baseTaskType = baseTaskType;
        result.ownSign = ownSign;
        result.taskType = ScheduleUtil.getTaskTypeByBaseAndOwnSign(baseTaskType, ownSign);
        result.ip = ScheduleUtil.getLocalIP();
        result.hostName = ScheduleUtil.getLocalHostName();
        result.registerTime = new Timestamp(scheduleTaskManager.getSystemTime());
        result.threadNum = threadNum;
        result.heartBeatTime = null;
        result.dealInfoDesc = "ScheduleServer初始化";
        result.version = 0;

        result.uuid = result.taskType+ "$" +result.ip + "$" + (UUID.randomUUID().toString().replaceAll("-", "").toUpperCase()) + "$" ;

        SimpleDateFormat DATA_FORMAT_yyyyMMdd = new SimpleDateFormat("yyMMdd");
        String s = DATA_FORMAT_yyyyMMdd.format(new Date(scheduleTaskManager.getSystemTime()));
        result.id = Long.parseLong(s) * 100000000 + Math.abs(result.uuid.hashCode() % 100000000);
        return result;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public Timestamp getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(Timestamp registerTime) {
        this.registerTime = registerTime;
    }

    public Timestamp getHeartBeatTime() {
        return heartBeatTime;
    }

    public void setHeartBeatTime(Timestamp heartBeatTime) {
        this.heartBeatTime = heartBeatTime;
    }

    public Timestamp getLastFetchDataTime() {
        return lastFetchDataTime;
    }

    public void setLastFetchDataTime(Timestamp lastFetchDataTime) {
        this.lastFetchDataTime = lastFetchDataTime;
    }

    public String getDealInfoDesc() {
        return dealInfoDesc;
    }

    public void setDealInfoDesc(String dealInfoDesc) {
        this.dealInfoDesc = dealInfoDesc;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }


    public Timestamp getCenterServerTime() {
        return centerServerTime;
    }

    public void setCenterServerTime(Timestamp centerServerTime) {
        this.centerServerTime = centerServerTime;
    }

    public String getNextRunStartTime() {
        return nextRunStartTime;
    }

    public void setNextRunStartTime(String nextRunStartTime) {
        this.nextRunStartTime = nextRunStartTime;
    }

    public String getNextRunEndTime() {
        return nextRunEndTime;
    }

    public void setNextRunEndTime(String nextRunEndTime) {
        this.nextRunEndTime = nextRunEndTime;
    }

    public String getOwnSign() {
        return ownSign;
    }

    public void setOwnSign(String ownSign) {
        this.ownSign = ownSign;
    }

    public String getBaseTaskType() {
        return baseTaskType;
    }

    public void setBaseTaskType(String baseTaskType) {
        this.baseTaskType = baseTaskType;
    }

    public long getId() {
        return id;
    }

    public void setRegister(boolean isRegister) {
        this.isRegister = isRegister;
    }

    public boolean isRegister() {
        return isRegister;
    }

    public void setManagerFactoryUUID(String managerFactoryUUID) {
        this.managerFactoryUUID = managerFactoryUUID;
    }

    public String getManagerFactoryUUID() {
        return managerFactoryUUID;
    }

    @Override
    public String toString() {
        return "ScheduleServer{" +
                "uuid='" + uuid + '\'' +
                ", id=" + id +
                ", managerFactoryUUID='" + managerFactoryUUID + '\'' +
                ", baseTaskType='" + baseTaskType + '\'' +
                ", taskType='" + taskType + '\'' +
                ", ownSign='" + ownSign + '\'' +
                ", ip='" + ip + '\'' +
                ", hostName='" + hostName + '\'' +
                ", threadNum=" + threadNum +
                ", registerTime=" + registerTime +
                ", heartBeatTime=" + heartBeatTime +
                ", lastFetchDataTime=" + lastFetchDataTime +
                ", nextRunStartTime='" + nextRunStartTime + '\'' +
                ", nextRunEndTime='" + nextRunEndTime + '\'' +
                ", centerServerTime=" + centerServerTime +
                ", version=" + version +
                ", isRegister=" + isRegister +
                ", dealInfoDesc='" + dealInfoDesc + '\'' +
                '}';
    }
}
