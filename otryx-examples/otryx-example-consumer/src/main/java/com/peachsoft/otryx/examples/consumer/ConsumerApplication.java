package io.peach.rpc.examples.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Peach RPC Consumer 示例应用。 */
@SpringBootApplication
public class ConsumerApplication {

    /** 创建 Consumer 示例。 */
    public ConsumerApplication() {
    }

    /**
     * 启动 Consumer。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
