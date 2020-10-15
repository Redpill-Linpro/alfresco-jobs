# Alfresco Jobs module

Used to simplify Alfresco jobs and make them cluster safe.

## Usage
Import the jar in your project by adding the Redpill Linpro repository and dependency to your pom.xml

```xml
<repositories>
...
    <repository>
        <id>redpill-linpro</id>
        <url>https://maven.redpill-linpro.com/nexus/content/groups/public</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>daily</updatePolicy>
        </snapshots>
    </repository>
...
</repositories>
```

```xml
<dependencies>
...
    <dependency>
        <groupId>org.redpill-linpro.alfresco.module</groupId>
        <artifactId>alfresco-jobs</artifactId>
        <version>1.0.1</version>
    </dependency>
...
</dependencies>
```

Extend the ClusteredExecuter class in your java class and configure your job. Use properties in alfresco-global.properties to control if the job should be enabled and at what time it should trigger (Example "example.job.enabled" & "example.job.cron")

```xml
...
  <bean id="ExampleJobDetail" class="org.springframework.scheduling.quartz.JobDetailFactoryBean">
    <property name="jobClass" value="org.redpill.alfresco.module.jobs.ExampleJob" />
    <property name="jobDataAsMap">
      <map>
        <entry key="repositoryState">
          <ref bean="repositoryState" />
        </entry>
        <entry key="transactionService">
          <ref bean="transactionService" />
        </entry>
        <entry key="jobLockService">
          <ref bean="jobLockService" />
        </entry>
      </map>
    </property>
  </bean>
  <bean id="ExampleJobAccessor" class="org.alfresco.schedule.AlfrescoSchedulerAccessorBean">
    <property name="scheduler" ref="schedulerFactory" />
    <property name="enabled" value="${example.job.enabled}" />
    <property name="triggers">
      <list>
        <bean id="ExampleJobTrigger" class="org.springframework.scheduling.quartz.CronTriggerFactoryBean">
          <property name="cronExpression" value="${example.job.cron}" />
          <property name="jobDetail" ref="ExampleJobDetail" />
        </bean>
      </list>
    </property>
  </bean>
...
```