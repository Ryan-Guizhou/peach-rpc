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

## 4. 发布到 Peach Nexus

框架本身不强制配置远程仓库，默认从 Maven Central 解析第三方依赖。需要给 `peach-cloud` 使用时，可通过部署环境的 `settings.xml` 和 `altDeploymentRepository` 或后续 Release Profile 发布到 Peach Nexus。

发布后业务项目只引入 `peach-rpc-spring-boot-starter`。
