package io.peach.rpc.spring.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Peach RPC Spring Boot 配置属性。
 */
@ConfigurationProperties(prefix = "peach.rpc")
public class PeachRpcProperties {

    /**
     * 创建 Peach RPC 配置属性。
     */
    public PeachRpcProperties() {
    }

    private boolean enabled = true;
    private final Registry registry = new Registry();
    private final Transport transport = new Transport();
    private final Client client = new Client();
    private final Server server = new Server();

    /**
     * 返回 Peach RPC 总开关。
     *
     * @return 是否启用 Peach RPC
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Peach RPC 总开关。
     *
     * @param enabled 是否启用 Peach RPC
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回注册中心配置。
     *
     * @return 注册中心配置
     */
    public Registry getRegistry() {
        return registry;
    }

    /**
     * 返回传输层配置。
     *
     * @return 传输层配置
     */
    public Transport getTransport() {
        return transport;
    }

    /**
     * 返回 Consumer 配置。
     *
     * @return Consumer 配置
     */
    public Client getClient() {
        return client;
    }

    /**
     * 返回 Provider 配置。
     *
     * @return Provider 配置
     */
    public Server getServer() {
        return server;
    }

    /**
     * 注册中心配置。
     */
    public static class Registry {

        /**
         * 创建注册中心配置。
         */
        public Registry() {
        }
        private String type = "memory";
        private String endpoints = "http://127.0.0.1:2379";
        private String namespace = "default";
        private long leaseTtlSeconds = 30;

        /**
         * 返回注册中心 SPI 名称。
         *
         * @return 注册中心 SPI 名称
         */
        public String getType() {
            return type;
        }

        /**
         * 设置注册中心 SPI 名称。
         *
         * @param type 注册中心 SPI 名称
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * 返回注册中心节点地址，多个地址使用逗号分隔。
         *
         * @return 注册中心节点地址，多个地址使用逗号分隔
         */
        public String getEndpoints() {
            return endpoints;
        }

        /**
         * 设置注册中心节点地址，多个地址使用逗号分隔。
         *
         * @param endpoints Etcd 节点地址，多个地址使用逗号分隔
         */
        public void setEndpoints(String endpoints) {
            this.endpoints = endpoints;
        }

        /**
         * 返回注册中心逻辑命名空间。
         *
         * @return 注册中心逻辑命名空间
         */
        public String getNamespace() {
            return namespace;
        }

        /**
         * 设置注册中心逻辑命名空间。
         *
         * @param namespace 注册中心逻辑命名空间
         */
        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        /**
         * 返回Etcd Lease TTL，单位秒。
         *
         * @return Etcd Lease TTL，单位秒
         */
        public long getLeaseTtlSeconds() {
            return leaseTtlSeconds;
        }

        /**
         * 设置Etcd Lease TTL，单位秒。
         *
         * @param leaseTtlSeconds Etcd Lease TTL，单位秒
         */
        public void setLeaseTtlSeconds(long leaseTtlSeconds) {
            this.leaseTtlSeconds = leaseTtlSeconds;
        }
    }

    /**
     * 传输层配置。
     */
    public static class Transport {

        /**
         * 创建传输层配置。
         */
        public Transport() {
        }
        private String type = "vertx";
        private int maxInflightPerConnection = 1024;
        private int maxFrameBytes = 16 * 1024 * 1024;
        private int maxWriteQueueBytes = 4 * 1024 * 1024;
        private Duration connectTimeout = Duration.ofSeconds(3);

        /**
         * 返回Transport SPI 名称。
         *
         * @return Transport SPI 名称
         */
        public String getType() {
            return type;
        }

        /**
         * 设置Transport SPI 名称。
         *
         * @param type Transport SPI 名称
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * 返回单连接最大并发请求数。
         *
         * @return 单连接最大并发请求数
         */
        public int getMaxInflightPerConnection() {
            return maxInflightPerConnection;
        }

        /**
         * 设置单连接最大并发请求数。
         *
         * @param maxInflightPerConnection 单连接最大并发请求数
         */
        public void setMaxInflightPerConnection(int maxInflightPerConnection) {
            this.maxInflightPerConnection = maxInflightPerConnection;
        }

        /**
         * 返回单帧最大字节数。
         *
         * @return 单帧最大字节数
         */
        public int getMaxFrameBytes() {
            return maxFrameBytes;
        }

        /**
         * 设置单帧最大字节数。
         *
         * @param maxFrameBytes 单帧最大字节数
         */
        public void setMaxFrameBytes(int maxFrameBytes) {
            this.maxFrameBytes = maxFrameBytes;
        }

        /**
         * 返回单连接写队列最大字节数。
         *
         * @return 单连接写队列最大字节数
         */
        public int getMaxWriteQueueBytes() {
            return maxWriteQueueBytes;
        }

        /**
         * 设置单连接写队列最大字节数。
         *
         * @param maxWriteQueueBytes 单连接写队列最大字节数
         */
        public void setMaxWriteQueueBytes(int maxWriteQueueBytes) {
            this.maxWriteQueueBytes = maxWriteQueueBytes;
        }

        /**
         * 返回TCP 建连超时时间。
         *
         * @return TCP 建连超时时间
         */
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * 设置TCP 建连超时时间。
         *
         * @param connectTimeout TCP 建连超时时间
         */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }
    }

    /**
     * Consumer 配置。
     */
    public static class Client {

        /**
         * 创建Consumer配置。
         */
        public Client() {
        }
        private boolean enabled = true;
        private Duration timeout = Duration.ofSeconds(3);
        private String loadBalancer = "p2c-ewma";
        private String proxy = "jdk";

        /**
         * 返回是否启用 Consumer。
         *
         * @return 是否启用 Consumer
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 Consumer。
         *
         * @param enabled 是否启用 Consumer
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回默认 RPC 调用超时时间。
         *
         * @return 默认 RPC 调用超时时间
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * 设置默认 RPC 调用超时时间。
         *
         * @param timeout 默认 RPC 调用超时时间
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        /**
         * 返回负载均衡 SPI 名称。
         *
         * @return 负载均衡 SPI 名称
         */
        public String getLoadBalancer() {
            return loadBalancer;
        }

        /**
         * 设置负载均衡 SPI 名称。
         *
         * @param loadBalancer 负载均衡 SPI 名称
         */
        public void setLoadBalancer(String loadBalancer) {
            this.loadBalancer = loadBalancer;
        }

        /**
         * 返回Proxy SPI 名称。
         *
         * @return Proxy SPI 名称
         */
        public String getProxy() {
            return proxy;
        }

        /**
         * 设置Proxy SPI 名称。
         *
         * @param proxy Proxy SPI 名称
         */
        public void setProxy(String proxy) {
            this.proxy = proxy;
        }
    }

    /**
     * Provider 配置。
     */
    public static class Server {

        /**
         * 创建Provider配置。
         */
        public Server() {
        }
        private boolean enabled;
        private String host = "0.0.0.0";
        private int port = 19090;
        private int maxConcurrent = 4096;

        /**
         * 返回是否启用 Provider。
         *
         * @return 是否启用 Provider
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 Provider。
         *
         * @param enabled 是否启用 Provider
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回Provider 监听地址。
         *
         * @return Provider 监听地址
         */
        public String getHost() {
            return host;
        }

        /**
         * 设置Provider 监听地址。
         *
         * @param host Provider 监听地址
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 返回Provider 监听端口。
         *
         * @return Provider 监听端口
         */
        public int getPort() {
            return port;
        }

        /**
         * 设置Provider 监听端口。
         *
         * @param port Provider 监听端口
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 返回Provider 最大并发业务请求数。
         *
         * @return Provider 最大并发业务请求数
         */
        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        /**
         * 设置Provider 最大并发业务请求数。
         *
         * @param maxConcurrent Provider 最大并发业务请求数
         */
        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }
    }
}
