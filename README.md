# 异步任务调度框架

## 说明

该框架用于持久化异步任务的调度，用于处理一些异步场景下的任务；

其中一个经典适用场景如下：

```
某个接口外部来了一个流量，接口中需要异步处理一些事情，在传统的处理中，一般有两种方案：
1、我们可能把这个需要异步做的事情同步去处理了，这样将会导致我们的接口缓慢无比；
2、将该任务放在线程池中执行，但是存在服务器宕机后该任务丢失的风险，此时需要做大量补偿逻辑或者干脆就不处理等着报错；

现在有了该框架后，我们可以使用该框架来调度我们的异步任务，同时框架保证服务器宕机后任务不会丢失，并且任务还会在服务的多个实例间自动负载均衡；
```

## 快速开始
### 引入依赖
如果使用了spring，那么可以使用如下依赖：
```xml
<dependency>
    <groupId>com.github.JoeKerouac</groupId>
    <artifactId>async-task-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```


如果未使用spring，那么应该使用下面的依赖：
```xml
<dependency>
    <groupId>com.github.JoeKerouac</groupId>
    <artifactId>async-task-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 初始化数据库，创建async_task表

> 注意，表名也可以自定义，但是需要额外的配置，这里就是用默认表名async_task了

```sql
create table if not exists `async_task`
(
    `request_id`       varchar(200)  not null comment '幂等ID',
    `task`             varchar(3000) not null comment '任务详情',
    `max_retry`        int           not null comment '最大可重试次数，-1表示无限重试',
    `exec_time`        datetime      not null comment '任务开始执行时间，重试时会更新',
    `processor`        varchar(100)  not null comment '任务执行器',
    `retry`            int           not null comment '当前重试次数',
    `status`           varchar(100)  not null comment '任务状态',
    `task_finish_code` varchar(100)  not null comment '任务执行结果码，任务执行完毕后才有意义，解释任务为什么结束',
    `create_ip`        varchar(100)  not null comment '创建任务的服务所在的机器IP',
    `exec_ip`          varchar(100)  not null comment '执行任务的服务所在的机器IP',
    `id`               varchar(100)  not null,
    `gmt_create_time`  datetime      not null,
    `gmt_update_time`  datetime      not null,
    `ext_map`          varchar(2000),
    primary key (`id`)

    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4 comment '异步任务表';

create unique index `idx_req` ON `async_task` (`request_id`);
create index `idx_load` ON `async_task` (`status`, `exec_time`) comment '捞取任务使用该索引';
create index `idx_clear` ON `async_task` (`task_finish_code`, `status`, `exec_time`) comment '清理任务使用该索引';

```

### 编程式使用

```java


import java.util.Collections;

import javax.sql.DataSource;

import com.github.joekerouac.async.task.exception.NoTransactionException;
import com.github.joekerouac.async.task.impl.AsyncTaskRepositoryImpl;
import com.github.joekerouac.async.task.model.AsyncServiceConfig;
import com.github.joekerouac.async.task.model.AsyncThreadPoolConfig;
import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.service.AsyncTaskServiceImpl;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.spi.MonitorService;
import com.github.joekerouac.async.task.spi.TransactionCallback;
import com.github.joekerouac.async.task.spi.TransactionHook;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
public class Test {

    public static void main(String[] args) {
        // TODO 这里替换为自己的数据源，该数据源中需要包含表async_task
        DataSource dataSource = null;

        AsyncServiceConfig config = new AsyncServiceConfig();
        config.setRepository(new AsyncTaskRepositoryImpl(dataSource));
        // 本地任务队列缓存大小，全量任务在数据库中，一定范围内本地缓存越大性能越好，但是缓存大占用内存也大，推荐100-300
        config.setCacheQueueSize(200);
        // 触发加载的队列长度阈值，当内存队列中的任务数量小于该值时将会触发从数据库中捞取数据
        config.setLoadThreshold(30);
        // 如果从数据库中没有捞取到数据，那么下次最小间隔多少毫秒才能再次捞取，防止数据库中没有任务时频繁的做空捞取，建议5秒；
        // 该值不建议太小，也不建议太大，因为太大的话如果当前有多台机器时达不到负载均衡的效果；
        config.setLoadInterval(5000);
        // 多久触发一次对异步任务系统的常规监控，例如打印当前队列数量等；
        config.setMonitorInterval(5000);
        // 异步任务执行线程池配置
        config.setThreadPoolConfig(new AsyncThreadPoolConfig());
        // ID生成器
        config.setIdGenerator(() -> {
            // TODO 注意这里自己实现下ID生成
            throw new RuntimeException("请实现ID生成");
        });

        // 编程的方式使用需要自己注册处理器
        config.setProcessors(Collections.singletonList(new TestTaskProcessor()));
        // 事务hook需要自己实现，如果不需要事务特性则不设置即可
        config.setTransactionHook(new TransactionHook() {
            @Override
            public boolean isActualTransactionActive() {
                return false;
            }

            @Override
            public void registerCallback(final TransactionCallback callback) throws NoTransactionException {

            }
        });
        // 监控服务也需要自己实现，系统有一个默认的监控，只打印了日志，用户可以自己在实现来做些其他事情
        config.setMonitorService(new MonitorService() {
        });

        AsyncTaskServiceImpl service = new AsyncTaskServiceImpl(config);
        // 注意，服务使用前一定要启动，使用后一定要关闭，否则可能资源泄露
        service.start();

        // 注意，requestId必须保证全局唯一，默认任务立即执行，失败后重试6次，重试6次不是立即重试，是有时间间隔的；
        service.addTask("123", new TestTask("JoeKerouac", 18));

        // 服务关闭的时候将异步任务服务关闭，也可以自己手动关闭
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop));
    }

    public static class TestTaskProcessor extends AbstractAsyncTaskProcessor<TestTask> {

        @Override
        public ExecResult process(final String requestId, final TestTask context) throws Throwable {
            // 这里放上处理逻辑，处理完后返回处理结果
            return ExecResult.SUCCESS;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TestTask {

        private String name;

        private int age;
    }

}

```

### spring boot的方式使用

#### 首先在`application.yaml`中提供如下配置：

```yaml
async:
  service:
    # 任务缓存队列大小，0表示队列无限长，队列设置太小可能会影响性能；
    cache-queue-size: 100
    # 触发捞取任务的队列长度阈值，当任务缓存队列的实际长度小于等于该值时会触发任务捞取，应该小于{@link #cacheQueueSize}；
    load-threshold: 30
    # 当上次任务捞取为空时下次任务捞取的最小时间间隔，当系统从repository中没有获取到任务后必须等待该时间间隔后才能再次捞取，单位毫秒，注意不要配置太大，不然
    # 应用的其他副本如果挂了，那个副本添加的任务就需要很长时间才会被本副本发现；
    load-interval: 5000
    # 触发定时监控的时间间隔，单位毫秒
    monitor-interval: 5000
    # 数据源名称，如果系统没有提供{@link com.github.joekerouac.async.task.spi.ConnectionSelector ConnectionSelector}这个bean，则需要提供数据源的名称
    data-source: "asyncDataSource"
    # 实际执行任务的线程池配置
    thread-pool-config:
      core-pool-size: 3
      thread-name: async-worker
  task:
    id:
      tag: test

```

#### 提供以下几个bean

> 如果系统中引入的有ID发号系统，则该章节大多数情况下可以忽略；

```java

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.joekerouac.async.task.spi.MonitorService;

/**
 * spring接入时需要提供的几个bean
 * 
 * MonitorService则是完全可以作为可选项，如果有需求了则可以选择实现，没有需求不提供该bean即可；
 *
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Configuration
public class TestConfig {

    @Bean
    public MonitorService monitorService() {
        // 这里做一个空实现，仅仅是为了示例展示，用户可以自行实现
        return new MonitorService() {

        };
    }

    @Bean
    public DataSource asyncDataSource() {
        // TODO 这里请自行构建数据源，注意这里的方法名（即生成的spring bean name）应该与配置文件中的async.service.data-source一致；
        return null;
    }

    @Bean
    public IDGenerator idGenerator() {
        return () -> UUID.randomUUID().toString();
    }
    
}

```

#### 准备我们的任务对象和对应的处理器

##### 任务对象

任务对象中包含本次要处理的任务的核心数据，用户可以自行根据实际任务设计对象；

> 注意，任务对象必须包含一个无参构造器，因为默认序列化的时候使用的是JSON，没有无参构造器会导致反序列化失败，如果无法包含无参构造器，请自行实现序列化/反序列化逻辑；

```java

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestTask {

    private String name;

    private int age;

}


```

##### 任务处理器

> 处理器上添加@AsyncTaskProcessor注解声明这是一个处理器，同时将会被注册成为spring的bean；

> 注意，这里的泛型TestTask就是我们上边声明的任务TestTask类型；

```java

import com.github.joekerouac.async.task.model.ExecResult;
import com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor;
import com.github.joekerouac.async.task.starter.annotations.AsyncTaskProcessor;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@AsyncTaskProcessor
public class TestTaskProcessor extends AbstractAsyncTaskProcessor<TestTask> {

    @Override
    public ExecResult process(final String requestId, final TestTask context) throws Throwable {
        // 这里放上处理逻辑，处理完后返回处理结果
        return ExecResult.SUCCESS;
    }
}


```

#### 在主类上添加@EnableAsyncTask注解

```java

import com.github.joekerouac.async.task.starter.annotations.EnableAsyncTask;
import org.springframework.boot.SpringApplication;

@EnableAsyncTask
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class);
    }
}
```


#### 异步任务系统已经就绪，开始使用

```java


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.joekerouac.async.task.AsyncTaskService;

/**
 * @author JoeKerouac
 * @date 2022-10-14 14:37:00
 * @since 1.0.0
 */
@Service
public class TestService {

    // 直接在我们的服务（bean）中注入异步任务的服务即可
    @Autowired
    private AsyncTaskService asyncTaskService;

    public void test() {
        // 创建一个任务
        TestTask task = new TestTask();
        // 添加该任务到异步任务处理系统，该任务将立即执行
        asyncTaskService.addTask("123", task);
    }

}

```
