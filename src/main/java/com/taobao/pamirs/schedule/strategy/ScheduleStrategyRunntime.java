package com.taobao.pamirs.schedule.strategy;


public class ScheduleStrategyRunntime {

    /** 任务名称 */
    String strategyName;
    String uuid;
    String ip;

    private Kind kind;

    //TODO:ZK节点中没有看到这个taskName,看看是不是可以删除掉
    /**  Schedule Name,Class Name、Bean Name */
    //private String taskName;

    private String taskParameter;

    //TODO:建议更名为 taskItemNum
    /** uuid机器参与执行strategyName任务 被分配的任务项数量  */
    int requestNum;

    /** 当前的任务数量 */
    int currentNum;

    String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    //public String getTaskName() {
    //    return taskName;
    //}
    //
    //public void setTaskName(String taskName) {
    //    this.taskName = taskName;
    //}

    public String getTaskParameter() {
        return taskParameter;
    }

    public void setTaskParameter(String taskParameter) {
        this.taskParameter = taskParameter;
    }

    public int getRequestNum() {
        return requestNum;
    }

    public void setRequestNum(int requestNum) {
        this.requestNum = requestNum;
    }

    public int getCurrentNum() {
        return currentNum;
    }

    public void setCurrentNum(int currentNum) {
        this.currentNum = currentNum;
    }

    @Override
    public String toString() {
        return "ScheduleStrategyRunntime [strategyName=" + strategyName
                + ", uuid=" + uuid + ", ip=" + ip + ", kind=" + kind
                +  ", taskParameter=" + taskParameter
                + ", requestNum=" + requestNum + ", currentNum=" + currentNum
                + ", message=" + message + "]";
    }


}
