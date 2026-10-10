package com.peachsoft.otryx.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * OTRYX RPC Core 的字节码级依赖边界约束。
 *
 * <p>测试仅扫描生产类，而不包含测试夹具。禁止 Core 直接耦合具体
 * Transport、Registry、Codec 与 Spring 适配器；技术集成必须经过
 * 对应的接口和独立 Adapter 模块。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/9 10:22
 */
class OtryxRpcCoreArchitectureTest {

    @Test
    void coreMustRemainIndependentOfInfrastructureFrameworks() {
        JavaClasses productionClasses =
                new ClassFileImporter()
                        .withImportOption(
                                ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.peachsoft.otryx");

        noClasses()
                .that()
                .resideInAPackage("com.peachsoft.otryx..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "io.vertx..",
                        "com.alibaba.nacos..",
                        "io.etcd..",
                        "org.apache.fory..",
                        "org.springframework..",
                        "com.peachsoft.otryx.transport.vertx..",
                        "com.peachsoft.otryx.registry.nacos..",
                        "com.peachsoft.otryx.registry.etcd..",
                        "com.peachsoft.otryx.codec.fory..")
                .check(productionClasses);
    }

    @Test
    void publicApiMustNotDependOnCoreRuntimeImplementations() {
        JavaClasses productionClasses =
                new ClassFileImporter()
                        .withImportOption(
                                ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.peachsoft.otryx.api");

        noClasses()
                .that()
                .resideInAPackage("com.peachsoft.otryx.api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.peachsoft.otryx.core..")
                .check(productionClasses);
    }
}
