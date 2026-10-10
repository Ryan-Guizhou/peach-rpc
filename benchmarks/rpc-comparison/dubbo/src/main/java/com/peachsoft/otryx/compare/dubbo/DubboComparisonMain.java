package com.peachsoft.otryx.compare.dubbo;

import com.peachsoft.otryx.compare.ComparisonHarness;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;

/**
 * Apache Dubbo 3.3.6 Dubbo TCP/Hessian2 独立进程对照实现。
 *
 * <p>使用官方 ServiceConfig/ReferenceConfig 点对点直连 API，避免额外依赖
 * Nacos 等注册中心，并且绝不与 Peach 的 Transport/Netty 类加载路径共用 JVM。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 14:27
 */
public final class DubboComparisonMain {

    private DubboComparisonMain() {
    }

    /**
     * 启动 Provider 或 Consumer 客户端负载测试。
     *
     * @param args provider port 或 client host port concurrency warmup_seconds
     *             duration_seconds payload_bytes output.json
     * @throws Exception 启动或执行失败
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "provider".equals(args[0])) {
            provider(Integer.parseInt(args[1]));
            return;
        }
        if (args.length == 8 && "client".equals(args[0])) {
            client(args);
            return;
        }
        throw new IllegalArgumentException(
                "Usage: provider <port> OR client <host> <port> "
                        + "<concurrency> <warmup_seconds> <duration_seconds> "
                        + "<payload_bytes> <output.json>");
    }

    private static void provider(int port) throws Exception {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid provider port");
        }
        ApplicationConfig application =
                new ApplicationConfig("rpc-compare-dubbo-provider");
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("dubbo");
        protocol.setPort(port);
        protocol.setSerialization("hessian2");
        protocol.setThreads(256);

        ServiceConfig<CompareEchoService> service = new ServiceConfig<>();
        service.setApplication(application);
        service.setRegistry(new RegistryConfig("N/A"));
        service.setProtocol(protocol);
        service.setInterface(CompareEchoService.class);
        service.setRef(input -> input);
        service.setVersion("1.0.0");
        service.setGroup("comparison");
        service.export();
        Runtime.getRuntime().addShutdownHook(
                new Thread(service::unexport, "dubbo-comparison-shutdown"));
        System.out.printf(
                "READY framework=dubbo protocol=dubbo serializer=hessian2 port=%d%n",
                port);
        new CountDownLatch(1).await();
    }

    private static void client(String[] args) throws Exception {
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        ApplicationConfig application =
                new ApplicationConfig("rpc-compare-dubbo-consumer");
        ReferenceConfig<CompareEchoService> reference =
                new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(new RegistryConfig("N/A"));
        reference.setInterface(CompareEchoService.class);
        reference.setVersion("1.0.0");
        reference.setGroup("comparison");
        reference.setUrl(
                "dubbo://" + host + ":" + port + "/"
                        + CompareEchoService.class.getName());
        reference.setTimeout(15000);
        MethodConfig echo = new MethodConfig();
        echo.setName("echo");
        echo.setRetries(0);
        reference.setMethods(List.of(echo));

        try {
            CompareEchoService service = reference.get();
            ComparisonHarness.run(
                    "dubbo", "dubbo-tcp", "hessian2", args, service::echo);
        } finally {
            reference.destroy();
        }
    }
}
