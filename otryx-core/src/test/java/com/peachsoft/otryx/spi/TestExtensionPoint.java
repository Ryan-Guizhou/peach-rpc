package io.peach.rpc.spi;

@SPI("one")
interface TestExtensionPoint {
    String value();
}
