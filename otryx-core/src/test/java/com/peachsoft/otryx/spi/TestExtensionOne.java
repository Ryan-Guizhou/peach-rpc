package com.peachsoft.otryx.spi;

@Extension("one")
public final class TestExtensionOne implements TestExtensionPoint {
    @Override
    public String value() {
        return "one";
    }
}
