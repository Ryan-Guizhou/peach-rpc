package io.peach.rpc.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Peach RPC Core 的字节码级依赖边界约束。
 *
 * <p>测试仅扫描生产类，而不包含测试夹具。禁止 Core 直接耦合具体
 * Transport、Registry、Codec 与 Spring 适配器；技术集成必须经过
 * 对应的接口和独立 Adapter 模块。
 */
class PeachRpcCoreArchitectureTest {

    @Test
    void coreMustRemainIndependentOfInfrastructureFrameworks() {
        JavaClasses productionClasses =
                new ClassFileImporter()
                        .withImportOption(
                                ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("io.peach.rpc");

        noClasses()
                .that()
                .resideInAPackage("io.peach.rpc..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "io.vertx..",
                        "com.alibaba.nacos..",
                        "io.etcd..",
                        "org.apache.fory..",
                        "org.springframework..",
                        "io.peach.rpc.transport.vertx..",
                        "io.peach.rpc.registry.nacos..",
                        "io.peach.rpc.registry.etcd..",
                        "io.peach.rpc.codec.fory..")
                .check(productionClasses);
    }

    @Test
    void publicApiMustNotDependOnCoreRuntimeImplementations() {
        JavaClasses productionClasses =
                new ClassFileImporter()
                        .withImportOption(
                                ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("io.peach.rpc.api");

        noClasses()
                .that()
                .resideInAPackage("io.peach.rpc.api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.peach.rpc.core..")
                .check(productionClasses);
    }
}
