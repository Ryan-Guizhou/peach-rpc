package com.peachsoft.otryx.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.peachsoft.otryx.observability.RpcSecurityMode;
import com.peachsoft.otryx.transport.RpcTransportSecurityOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Vert.x TLS material 状态检测测试。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/29 17:34
 */
class VertxTlsSupportTest {

    @Test
    void fileStateShouldDetectContentChangeWithSameSizeAndMtime()
            throws Exception {
        Path file = Files.createTempFile(
                "otryx-tls-state",
                ".pem");
        try {
            Files.writeString(file, "AAAA");
            FileTime originalTime =
                    Files.getLastModifiedTime(file);

            RpcTransportSecurityOptions security =
                    new RpcTransportSecurityOptions(
                            RpcSecurityMode.TLS,
                            file.toString(),
                            "",
                            "",
                            true,
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(1),
                            Duration.ofDays(1));

            VertxTlsSupport.FileState before =
                    VertxTlsSupport.fileState(security);

            Files.writeString(file, "BBBB");
            Files.setLastModifiedTime(
                    file,
                    originalTime);

            VertxTlsSupport.FileState after =
                    VertxTlsSupport.fileState(security);

            assertNotEquals(before, after);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
