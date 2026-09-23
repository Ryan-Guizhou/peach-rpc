package io.peach.rpc.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Peach RPC Spring Boot 示例应用。
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * 创建示例应用。
     */
    public DemoApplication() {
    }

    /**
     * 启动示例应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
