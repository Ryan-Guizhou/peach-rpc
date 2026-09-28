package io.peach.rpc.examples.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Peach RPC Provider 示例应用。 */
@SpringBootApplication
public class ProviderApplication {

    /** 创建 Provider 示例。 */
    public ProviderApplication() {
    }

    /**
     * 启动 Provider。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
    }
}
