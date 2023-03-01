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