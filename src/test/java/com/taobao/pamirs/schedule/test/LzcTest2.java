package com.taobao.pamirs.schedule.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.taobao.pamirs.schedule.strategy.ManagerFactoryInfo;

import java.sql.Timestamp;

public class LzcTest2 {
    static  Gson gson = new GsonBuilder().create();


    public static void main(String [] args){
        ManagerFactoryInfo info=new ManagerFactoryInfo();
        info.setUuid("10.187.133.52$host-10-187-133-52$786D516F7FA84FB6BF501B9B4FFF073D$0000000477");
        info.setStart(true);
        String str=gson.toJson(info);
        System.out.println(str);
    }
}