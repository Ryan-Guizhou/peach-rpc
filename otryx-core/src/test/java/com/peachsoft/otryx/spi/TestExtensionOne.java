package com.peachsoft.otryx.spi;

/**
 * 提供 SPI 扩展加载测试使用的具名实现。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
 */
@Extension("one")
public final class TestExtensionOne implements TestExtensionPoint {
    @Override
    public String value() {
        return "one";
    }
}
