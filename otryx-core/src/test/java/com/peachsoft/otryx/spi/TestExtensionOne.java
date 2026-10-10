package io.peach.rpc.spi;

@Extension("one")
public final class TestExtensionOne implements TestExtensionPoint {
    @Override
    public String value() {
        return "one";
    }
}
