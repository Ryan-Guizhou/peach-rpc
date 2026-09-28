package io.peach.rpc.registry.nacos;

/** Peach RPC 在 Nacos metadata 中保留的键。 */
final class NacosReservedMetadata {

    static final String PREFIX = "peach.rpc.";
    static final String INSTANCE_ID = PREFIX + "instance-id";
    static final String INTERFACE = PREFIX + "interface";
    static final String VERSION = PREFIX + "version";
    static final String GROUP = PREFIX + "group";
    static final String PROTOCOL = PREFIX + "protocol";
    static final String CLUSTER = PREFIX + "cluster";

    private NacosReservedMetadata() {
    }

    static boolean reserved(String key) {
        return key != null && key.startsWith(PREFIX);
    }
}
