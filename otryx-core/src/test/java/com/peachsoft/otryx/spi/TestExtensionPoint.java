package com.peachsoft.otryx.spi;

/**
 * 定义 SPI 扩展加载测试的服务接口。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
@SPI("one")
interface TestExtensionPoint {
    String value();
}
