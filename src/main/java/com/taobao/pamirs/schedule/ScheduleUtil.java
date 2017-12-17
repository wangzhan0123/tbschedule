package com.taobao.pamirs.schedule;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * 调度处理工具类
 * @author xuannan
 *
 */
public class ScheduleUtil {
    public static String OWN_SIGN_BASE = "BASE";

    public static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }

    public static int getFreeSocketPort() {
        try {
            ServerSocket ss = new ServerSocket(0);
            int freePort = ss.getLocalPort();
            ss.close();
            return freePort;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    public static String transferDataToString(Date d) {
        SimpleDateFormat DATA_FORMAT_yyyyMMddHHmmss = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return DATA_FORMAT_yyyyMMddHHmmss.format(d);
    }

    public static Date transferStringToDate(String d) throws ParseException {
        SimpleDateFormat DATA_FORMAT_yyyyMMddHHmmss = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return DATA_FORMAT_yyyyMMddHHmmss.parse(d);
    }

    public static Date transferStringToDate(String d, String formate) throws ParseException {
        SimpleDateFormat FORMAT = new SimpleDateFormat(formate);
        return FORMAT.parse(d);
    }

    public static String getTaskTypeByBaseAndOwnSign(String baseType, String ownSign) {
        if (ownSign.equals(OWN_SIGN_BASE) == true) {
            return baseType;
        }
        return baseType + "$" + ownSign;
    }

    //TODO:这个方法重点注意，通过taskType可以得到baseTaskType,也就是说taskType一定包含了baseTaskType
    public static String splitBaseTaskTypeFromTaskType(String taskType) {
        if (taskType.indexOf("$") >= 0) {
            return taskType.substring(0, taskType.indexOf("$"));
        } else {
            return taskType;
        }

    }

    //TODO:是不是可以将方法名改成splitOwnsignFromTaskName
    public static String splitOwnsignFromTaskType(String taskType) {
        if (taskType.indexOf("$") >= 0) {
            return taskType.substring(taskType.indexOf("$") + 1);
        } else {
            return OWN_SIGN_BASE;
        }
    }

    /**
     * 计算每个机器被分配的任务项数量
     *
     * @param serverNum 总的服务器数量
     * @param taskItemNum 分配的线程组数量(1项任务分配给多少个团队来执行)
     * @param @Deprecated numOfSingleServer 分配的单JVM最大线程组数量(1项任务在1个房子<机器>里最多允许多少个团队来执行)
     * @return
     */
    public static int[] assignTaskNumber(int serverNum, int taskItemNum, int numOfSingleServer) {
        int[] taskNums = new int[serverNum];
        int numOfSingle = taskItemNum / serverNum;
        int otherNum = taskItemNum % serverNum;
        //20150323 删除, 任务分片保证分配到所有的线程组数上。 开始
//		if (numOfSingleServer >0 && numOfSingle >= numOfSingleServer) {
//			numOfSingle = numOfSingleServer;
//			otherNum = 0;
//		}
        //20150323 删除, 任务分片保证分配到所有的线程组数上。 结束
        for (int i = 0; i < taskNums.length; i++) {
            if (i < otherNum) {
                taskNums[i] = numOfSingle + 1;
            } else {
                taskNums[i] = numOfSingle;
            }
        }
        return taskNums;
    }

    private static String printArray(int[] items) {
        String s = "";
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                s = s + ",";
            }
            s = s + items[i];
        }
        return s;
    }

    public static void main(String[] args) {
        System.out.println(printArray(assignTaskNumber(1, 10, 0)));
        System.out.println(printArray(assignTaskNumber(2, 10, 0)));
        System.out.println(printArray(assignTaskNumber(3, 10, 0)));
        System.out.println(printArray(assignTaskNumber(4, 10, 0)));
        System.out.println(printArray(assignTaskNumber(5, 10, 0)));
        System.out.println(printArray(assignTaskNumber(6, 10, 0)));
        System.out.println(printArray(assignTaskNumber(7, 10, 0)));
        System.out.println(printArray(assignTaskNumber(8, 10, 0)));
        System.out.println(printArray(assignTaskNumber(9, 10, 0)));
        System.out.println(printArray(assignTaskNumber(10, 10, 0)));

        System.out.println("-----------------");

        System.out.println(printArray(assignTaskNumber(1, 10, 3)));
        System.out.println(printArray(assignTaskNumber(2, 10, 3)));
        System.out.println(printArray(assignTaskNumber(3, 10, 3)));
        System.out.println(printArray(assignTaskNumber(4, 10, 3)));
        System.out.println(printArray(assignTaskNumber(5, 10, 3)));
        System.out.println(printArray(assignTaskNumber(6, 10, 3)));
        System.out.println(printArray(assignTaskNumber(7, 10, 3)));
        System.out.println(printArray(assignTaskNumber(8, 10, 3)));
        System.out.println(printArray(assignTaskNumber(9, 10, 3)));
        System.out.println(printArray(assignTaskNumber(10, 10, 3)));

    }
}
