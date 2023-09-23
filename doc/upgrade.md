# v1.0.1
- 优化项目启动，如果有processor创建失败则直接抛出异常阻止启动，而不是在运行时报processor找不到；

# v2.0.0
- 增加任务执行超时提醒；
- 监控接口重建；（接口中方法不再有default实现，增加一个`com.github.joekerouac.async.task.impl.MonitorServiceAdaptor`类帮助使用方快速构建监控）
- 增加任务取消接口；
- 该版本之前仅支持全局级别的任务清理，如果配置自动清理则所有类型的任务都会自动清理，现在优化该逻辑，支持processor级别的任务自动清理，允许仅清理某些类型的任务，同时不同任务类型的保留时长允许不一样；

# v2.0.1
- 优化processor添加流程，spring场景下尽可能的延迟processor创建；
- 修复2.0.0引入的BUG，调用`com.github.joekerouac.async.task.AsyncTaskService.addProcessor`将会导致processor强制加入自动清理的任务中，而不会判断processor需不需要自动清理；

# v2.0.2
- 支持trace
- 支持线程池隔离，不同的task使用不同的线程池执行；


# v2.0.3
- 调度器优化，任务执行失败进入重试队列时不直接添加到内存中，否则在大量失败时队列中将存满失败任务，新的任务无法执行；
- 任务捞取sql bug修复，我们期望按照执行时间升序捞取，老版本中是按照执行时间降序捞取的，这个版本修复;
- 支持自定义任务执行引擎；
- 修复BUG：`com.github.joekerouac.async.task.service.DefaultAsyncTaskProcessorEngine.queue`的排序方法有问题

## buf fix
`com.github.joekerouac.async.task.service.DefaultAsyncTaskProcessorEngine.queue`的排序方法有问题，原排序方法：

```
queue = new TreeSet<>((t0, t1) -> (int)(t0.getValue().atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
          - t1.getValue().atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()));
```

将long类型的结果强转为了int值，存在溢出的问题，同时鉴于`java.time.LocalDateTime`实现了`java.lang.Comparable`接口，可以直接调用其`compare`方法作为排序器；

# v2.0.4
- 潜在风险优化：在取消任务时不再死循环直到任务取消成功（某些场景可能死循环），由外部自行处理；
- 修改`com.github.joekerouac.async.task.spi.TraceService`定义；**不兼容**
- 修复`com.github.joekerouac.async.task.spi.AbstractAsyncTaskProcessor.nextExecTimeInterval`这里的潜在数组越界问题；
- 修复潜在BUG：原任务表中的`exec_time`定义类型`datetime`，默认不包含毫秒，当我们插入的时间包含毫秒时，该时间将会自动把毫秒四舍五入到秒上，有可能导致时间插入数据库后变小或者变大，如果变小几乎无影响，但是一旦时间变大，执行任务时由于会重新从数据库加载任务，此时加载出来的时间是变大了的，此时会兜底判断任务是否到执行时间，这就会导致我们认为该任务并不到执行时间，最终导致该任务被丢弃（没有重新入队列），一直到下次从数据库捞取任务时才会捞起执行，导致任务执行变慢；
- 优化数据库类型解析器；
- 优化流式任务，原流式任务子任务的执行结果处理有问题，无论返回什么都认为是SUCCESS，修改逻辑，子任务执行结果返回什么，实际代理执行的异步任务就返回什么；
- 优化流式任务，FlowService增加节点唤醒能力；
- 修复流式任务bug: 对于无限流任务，如果在构建执行链的时候，库中只有一个INIT状态的任务，将会构建失败；

# v3.0.0
- 事务相关内容重构；
- 修复流式任务中事务不生效问题；

# v3.0.1
- 优化任务关闭处理；
- 优化任务排序，优先处理本机的任务；

