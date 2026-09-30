# Maven 结构与发布

## 1. 版本基线

根 POM 使用 `${revision}` 统一版本，并通过 `dependencyManagement` 管理 Spring Boot、Vert.x、Jetcd、Fory、CGLIB 和 JMH 版本。子模块不得重复声明这些版本。

当前 Spring Boot 固定为 3.5.4，与 `peach-cloud` 保持一致。

## 2. Reactor 顺序

```text
peach-rpc-core
├── peach-rpc-codec-fory
├── peach-rpc-transport-vertx
├── peach-rpc-registry-etcd
├── peach-rpc-proxy-cglib
└── peach-rpc-spring-boot-autoconfigure
    └── peach-rpc-spring-boot-starter
        └── peach-rpc-examples
peach-rpc-benchmarks
```

Maven 会根据模块依赖自动确定实际构建顺序。

## 3. 验证

```bash
mvn -B -ntp clean verify -Pquality
```

`quality` Profile 会执行 Javadoc doclint。CI 还会先运行 `scripts/check_project.py` 检查模块、文档、Core 依赖边界和 Java 基础格式。

## 4. 发布产物

正式 Release 前先执行：

~~~bash
python3 scripts/check_project.py
python3 scripts/check_release_readiness.py
mvn -B -ntp clean verify -Pquality
~~~

Release Readiness Workflow 会生成 Reactor JAR Inventory，用于确认预期模块均已产生 Artifact。

Core 额外附带 tests classifier 的 test-jar，仅用于 Registry Adapter 的共享 Contract TestKit，不是业务运行时依赖。

## 5. 发布到 Peach Nexus

框架本身不强制配置远程仓库，默认从 Maven Central 解析第三方依赖。发布环境通过受控 `settings.xml` 与目标 Deployment Repository 执行 Maven deploy；仓库不保存 Nexus Credential。

发布前必须：

1. 使用非 SNAPSHOT revision；
2. Release Readiness 与 quality build 全绿；
3. Source/Javadoc Artifact 可生成；
4. Release Notes 与 Upgrade/Rollback 说明完成；
5. RC/GA 对应 Tag 与版本一致。

发布后业务项目只引入 `peach-rpc-spring-boot-starter`，不要直接依赖 benchmarks、examples 或 Core test-jar。

完整版本、弃用、RC/GA 与 Release Notes 规则见 [发布策略与 Production Operations](release-policy.md)。
