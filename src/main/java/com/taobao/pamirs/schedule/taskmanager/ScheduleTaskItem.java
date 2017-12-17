package com.taobao.pamirs.schedule.taskmanager;

/**
 * 任务队列类型
 * @author xuannan
 *
 */
public class ScheduleTaskItem {
    public enum TaskItemSts { ACTIVTE, FINISH, HALT }

    /**
     * 原始任务类型
     */
    private String baseTaskType;

    /**
     * 处理任务类型
     */
    private String taskType;

    /**
     * 队列的环境标识
     */
    private String ownSign;

    /**
     * 任务队列ID
     */
    private String taskItem;
    /**
     * 任务处理需要的参数
     */
    private String dealParameter = "";

    /**
     * 持有当前任务队列的任务处理器
     */
    private String currentScheduleServer;
    /**
     * 正在申请此任务队列的任务处理器
     */
    private String requestScheduleServer;

    /**
     * 任务处理情况,用于任务处理器会写一些信息
     */
    private String dealDesc = "";

    /**
     * 数据版本号
     */
    private long version;

    /**
     * 完成状态
     */
    private TaskItemSts sts = TaskItemSts.ACTIVTE;


    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTaskItem() {
        return taskItem;
    }

    public void setTaskItem(String aTaskItem) {
        this.taskItem = aTaskItem;
    }

    public String getCurrentScheduleServer() {
        return currentScheduleServer;
    }

    public void setCurrentScheduleServer(String currentScheduleServer) {
        this.currentScheduleServer = currentScheduleServer;
    }

    public String getRequestScheduleServer() {
        return requestScheduleServer;
    }

    public void setRequestScheduleServer(String requestScheduleServer) {
        this.requestScheduleServer = requestScheduleServer;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getOwnSign() {
        return ownSign;
    }

    public void setOwnSign(String ownSign) {
        this.ownSign = ownSign;
    }

    public void setDealDesc(String dealDesc) {
        this.dealDesc = dealDesc;
    }

    public String getDealDesc() {
        return dealDesc;
    }

    public void setSts(TaskItemSts sts) {
        this.sts = sts;
    }

    public TaskItemSts getSts() {
        return sts;
    }

    public void setDealParameter(String dealParameter) {
        this.dealParameter = dealParameter;
    }

    public String getDealParameter() {
        return dealParameter;
    }

    public String getBaseTaskType() {
        return baseTaskType;
    }

    public void setBaseTaskType(String baseTaskType) {
        this.baseTaskType = baseTaskType;
    }

//    public String toString() {
//        return "TASK_TYPE=" + this.taskType + ":TASK_ITEM=" + this.taskItem
//                + ":CUR_SERVER=" + this.currentScheduleServer + ":REQ_SERVER=" + this.requestScheduleServer + ":DEAL_PARAMETER=" + this.dealParameter;
//    }


    @Override
    public String toString() {
        return "ScheduleTaskItem{" +
                "baseTaskType='" + baseTaskType + '\'' +
                ", taskType='" + taskType + '\'' +
                ", ownSign='" + ownSign + '\'' +
                ", taskItem='" + taskItem + '\'' +
                ", dealParameter='" + dealParameter + '\'' +
                ", currentScheduleServer='" + currentScheduleServer + '\'' +
                ", requestScheduleServer='" + requestScheduleServer + '\'' +
                ", dealDesc='" + dealDesc + '\'' +
                ", version=" + version +
                ", sts=" + sts +
                '}';
    }
}
