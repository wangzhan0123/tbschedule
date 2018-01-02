<bean id="scheduleManagerFactory" class="com.taobao.pamirs.schedule.strategy.TBScheduleManagerFactory"
      init-method="init">
    <property name="zkConfig">
        <map>
            <entry key="zkConnectString" value="${mvn.tbSchedule.zkConnectString}" />
            <entry key="rootPath" value="${mvn.tbSchedule.rootPath}" />
            <entry key="zkSessionTimeout" value="${mvn.tbSchedule.zkSessionTimeout}" />
            <entry key="userName" value="${mvn.tbSchedule.userName}" />
            <entry key="password" value="${mvn.tbSchedule.password}" />
            <entry key="isCheckParentPath" value="${mvn.thSchedule.isCheckParentPath}" />
        </map>
    </property>
</bean>


<!-- tbSchedule maven profile参数 -->
<mvn.tbSchedule.zkConnectString>192.168.202.106:2181</mvn.tbSchedule.zkConnectString>
<mvn.tbSchedule.rootPath>/o2oworkerpath/o2o-message-center</mvn.tbSchedule.rootPath>
<mvn.tbSchedule.zkSessionTimeout>60000</mvn.tbSchedule.zkSessionTimeout>
<mvn.tbSchedule.userName>o2o-message-center</mvn.tbSchedule.userName>
<mvn.tbSchedule.password>8570b864b5ee43368c208b92f4587a22</mvn.tbSchedule.password>
<mvn.thSchedule.isCheckParentPath>true</mvn.thSchedule.isCheckParentPath>



