# Maven 结构与发布

## 1. 版本基线

根 POM 使用 `${revision}` 统一版本。

默认 GA：

```xml
<revision>1.0.0</revision>
```

RC1 构建通过命令行覆盖：

```bash
mvn -B -ntp -Drevision=1.0.0-RC1 clean verify -Pquality,release
```

运行环境：

- JDK 21；
- Spring Boot 3.5.4。

## 2. Reactor

```text
peach-rpc-core
├── peach-rpc-codegen
├── peach-rpc-codec-fory
├── peach-rpc-transport-vertx
├── peach-rpc-registry-etcd
├── peach-rpc-registry-nacos
├── peach-rpc-proxy-cglib
├── peach-rpc-proxy-bytebuddy
├── peach-rpc-observability-micrometer
├── peach-rpc-observability-opentelemetry
├── peach-rpc-observability-jfr
└── peach-rpc-spring-boot-autoconfigure
    └── peach-rpc-spring-boot-starter
peach-rpc-examples
peach-rpc-benchmarks
```

实际构建顺序由 Maven 依赖图决定。

## 3. 本地验证

```bash
python3 scripts/check_project.py
mvn -B -ntp clean verify -Pquality
```

`quality` Profile 执行 Javadoc doclint。

## 4. Release Profile

`release` Profile 附加 Javadoc JAR；Source JAR 默认附加。

GA：

```bash
mvn -B -ntp clean verify -Pquality,release
```

RC1：

```bash
mvn -B -ntp -Drevision=1.0.0-RC1 clean verify -Pquality,release
```

## 5. GitHub Release

`.github/workflows/release.yml` 会：

1. 校验 RC1/GA 版本；
2. 执行 Repository/Release Readiness；
3. 完整构建；
4. 生成 Artifact Inventory；
5. 生成 SHA256SUMS；
6. 生成 Release Bundle；
7. 可选创建 GitHub Release。

发布凭据不写入仓库。

## 6. 本地使用

业务推荐依赖：

```xml
<dependency>
    <groupId>io.peach.rpc</groupId>
    <artifactId>peach-rpc-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果目标 Maven Repository 尚未提供 1.0.0，可以先在源码根目录：

```bash
mvn -B -ntp clean install -DskipTests
```

然后从本地 Maven Repository 使用同一坐标。

## 7. 发布边界

项目当前不在 POM 中硬编码某个私有 Nexus/Artifactory。需要部署到组织仓库时，通过组织自己的 Maven `settings.xml` / deployment policy 管理 Credential。

版本、RC/GA、Release Notes 规则见 [发布策略](release-policy.md)。
