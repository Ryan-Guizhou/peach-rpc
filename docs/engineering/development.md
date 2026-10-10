# OTRYX RPC 开发指南

## 1. 环境

- JDK 21；
- Maven 3.9+；
- Python 3（Repository/Performance scripts）；
- Docker（Registry Integration/Chaos/Examples）。

## 2. Java 规范

- UTF-8，无 BOM；
- 4 空格，不使用 Tab；
- 不使用 wildcard import；
- 单行不超过 120 字符；
- 公共/受保护框架 API 使用标准中文 Javadoc；
- Runtime 日志使用英文 SLF4J 参数化消息；
- 不使用 `System.out/System.err`；
- 不记录 Secret 或完整业务参数。

## 3. 架构边界

Core 禁止直接依赖：

- Spring；
- Vert.x；
- Etcd；
- Nacos；
- Fory；
- CGLIB；
- Micrometer；
- OpenTelemetry；
- JFR。

第三方实现通过 Adapter 进入。

## 4. 基础验证

```bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
```

## 5. 修改类型与额外门禁

| 改动 | 额外要求 |
|---|---|
| Registry | Integration + 对应 Chaos |
| Wire/Schema | Rolling Compatibility |
| Transport | Race/Heartbeat/Drain/TLS |
| Security | TLS/mTLS real socket tests |
| Hot path | JMH / soak / allocation Evidence |
| Public API | Compatibility review + docs |
| Release | Release Readiness |

## 6. 热路径 Review

必须说明：

- 对象分配；
- 锁/CAS；
- Map/Queue；
- 线程切换；
- 序列化/复制；
- 系统调用；
- tail latency；
- failure behavior。

不能只凭“代码看起来更快”合并性能优化。

## 7. 新增 Adapter

1. 新建独立模块；
2. 不把第三方类型泄漏到 Core；
3. 使用稳定 SPI 名称；
4. 明确生命周期；
5. 定义阻塞/线程边界；
6. 复用 Contract TestKit；
7. 增加故障/恢复测试；
8. 更新文档。

## 8. 文档

改变用户可见行为时同步：

- README 中英文；
- 相关设计/配置文档；
- CHANGELOG/Release Notes（若属于发布内容）；
- Mermaid/UML。

## 9. PR

使用仓库 PR Template。所有 PR 都必须可解释、可测试、可回滚。
