package io.peach.rpc.registry.nacos;

import com.alibaba.nacos.api.naming.pojo.Instance;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import java.util.HashMap;
import java.util.Map;

/** Nacos Instance 与 Peach RPC ServiceInstance 的映射器。 */
final class NacosInstanceMapper {

    static final int CORE_WEIGHT_SCALE = 100;

    private NacosInstanceMapper() {
    }

    static Instance toNacos(ServiceInstance source, String cluster) {
        Map<String, String> metadata = new HashMap<>(source.metadata());
        for (String key : metadata.keySet()) {
            if (NacosReservedMetadata.reserved(key)) {
                throw new IllegalArgumentException(
                        "RPC metadata must not override reserved key: " + key);
            }
        }
        metadata.put(NacosReservedMetadata.INSTANCE_ID, source.instanceId());
        metadata.put(
                NacosReservedMetadata.INTERFACE,
                source.serviceKey().serviceName());
        metadata.put(
                NacosReservedMetadata.VERSION,
                source.serviceKey().version());
        metadata.put(
                NacosReservedMetadata.GROUP,
                source.serviceKey().group());
        metadata.put(NacosReservedMetadata.PROTOCOL, "peach-rpc");
        metadata.put(NacosReservedMetadata.CLUSTER, cluster);

        Instance target = new Instance();
        target.setIp(source.endpoint().host());
        target.setPort(source.endpoint().port());
        target.setWeight(source.weight() / (double) CORE_WEIGHT_SCALE);
        target.setClusterName(cluster);
        target.setEphemeral(true);
        target.setEnabled(true);
        target.setMetadata(metadata);
        return target;
    }

    static ServiceInstance fromNacos(ServiceKey key, Instance source) {
        if (source == null
                || !source.isHealthy()
                || !source.isEnabled()
                || source.getWeight() <= 0D
                || source.getIp() == null
                || source.getIp().isBlank()
                || source.getPort() <= 0
                || source.getPort() > 65_535) {
            return null;
        }
        double scaled = source.getWeight() * CORE_WEIGHT_SCALE;
        if (!Double.isFinite(scaled) || scaled > Integer.MAX_VALUE) {
            return null;
        }
        int weight = Math.max(1, (int) Math.round(scaled));
        Map<String, String> metadata = source.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(source.getMetadata());
        String instanceId = metadata.get(NacosReservedMetadata.INSTANCE_ID);
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = fallbackInstanceId(key, source);
        }
        return new ServiceInstance(
                instanceId,
                key,
                new RpcEndpoint(source.getIp(), source.getPort()),
                weight,
                Map.copyOf(metadata));
    }

    private static String fallbackInstanceId(
            ServiceKey key,
            Instance source) {
        String cluster = source.getClusterName() == null
                ? NacosRegistryFactory.DEFAULT_CLUSTER
                : source.getClusterName();
        return key.canonicalName()
                + '@'
                + cluster
                + '@'
                + source.getIp()
                + ':'
                + source.getPort();
    }
}
