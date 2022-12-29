# v1.0.1
- 优化项目启动，如果有processor创建失败则直接抛出异常阻止启动，而不是在运行时报processor找不到；

# v2.0.0
- 增加任务执行超时提醒；
- 监控接口重建；（接口中方法不再有default实现，增加一个`com.github.joekerouac.async.task.impl.MonitorServiceAdaptor`类帮助使用方快速构建监控）
- 增加任务取消接口；