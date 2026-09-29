# Peach RPC TLS / mTLS 安全指南

> 适用版本：V2-C.3 当前开发分支。  
> TLS/mTLS 位于 Transport 层，不改变 Peach RPC v1 固定协议头，也不替代业务授权。

## 1. 定位

Peach RPC 的安全链路为：

~~~text
PeachRpcClient / PeachRpcServer
        |
Peach RPC Protocol
        |
TLS / mTLS
        |
Vert.x TCP
        |
Network
~~~

三种模式：

| 模式 | 通道加密 | Consumer 校验 Provider | Provider 校验 Consumer |
|---|---:|---:|---:|
| PLAINTEXT | 否 | 否 | 否 |
| TLS | 是 | 是 | 否 |
| MTLS | 是 | 是 | 是 |

默认仍为 `PLAINTEXT`，保证现有应用升级后行为不变。

## 2. Spring Boot 配置

~~~yaml
peach:
  rpc:
    transport:
      security:
        mode: MTLS
        certificate-path: /etc/peach-rpc/tls/tls.crt
        private-key-path: /etc/peach-rpc/tls/tls.key
        trust-certificate-path: /etc/peach-rpc/tls/ca.crt
        hostname-verification: true
        handshake-timeout: 3s
        reload-interval: 30s
        expiry-warning-threshold: 7d
~~~

### TLS Provider

Provider 必须配置：

- `certificate-path`
- `private-key-path`

### TLS Consumer

Consumer 必须配置：

- `trust-certificate-path`

默认启用 Hostname Verification。

### mTLS

Provider 与 Consumer 都必须配置：

- certificate
- private key
- trust certificate

Provider 使用 `ClientAuth.REQUIRED`，因此缺少合法客户端证书时握手失败。

## 3. 连接生命周期

TLS/mTLS 在 Peach RPC HELLO 之前完成：

~~~mermaid
sequenceDiagram
    participant C as Consumer
    participant P as Provider

    C->>P: TCP connect
    C->>P: TLS ClientHello
    P->>C: TLS certificate
    opt mTLS
        C->>P: Client certificate
    end
    Note over C,P: certificate verification
    C->>P: Peach HELLO
    P->>C: HELLO_ACK
    Note over C,P: ACTIVE
~~~

TLS 失败不会 fallback 到 plaintext。

## 4. 证书校验

启动阶段会：

1. 校验证书/私钥/CA 文件可读；
2. 解析 X.509 certificate；
3. 执行有效期校验；
4. 对临近过期证书产生 Observer warning；
5. 构造 Vert.x SSL options。

无效或过期证书会 fail-fast。

## 5. Hostname Verification

默认开启：

~~~yaml
peach.rpc.transport.security.hostname-verification: true
~~~

Consumer 通过目标 RPC host 与证书身份执行校验。

生产环境不建议关闭。测试已覆盖证书主机名与目标 host 不匹配时握手失败。

## 6. 在线证书 Reload

Peach RPC 周期检查以下 material 的 SHA-256 内容指纹、mtime 与 size：

- server certificate
- private key
- trust certificate

文件状态发生变化后：

~~~text
file changed
   -> parse and validate new material
   -> updateSSLOptions
   -> new connection uses new certificate
   -> existing connection remains alive
~~~

新 material 无效时：

~~~text
reload validation failed
   -> report FAILURE observation
   -> keep previous valid SSL material
   -> existing/new connection continues with previous material
~~~

不会因为换证主动批量断开所有长连接。

## 7. 与 V2-C.2 HA 的协同

TLS 已通过以下组合测试：

- TLS + Peach HELLO/ACK；
- TLS + Heartbeat；
- TLS 长时间空闲后继续 RPC；
- Provider restart；
- 同一个 TLS Consumer 不重启恢复调用；
- reconnect/backoff 与 TLS 重建共同工作。

因此 TLS 不是旁路实现，而是现有 Connection HA 生命周期的一部分。

## 8. 可观测事件

Core `RpcObserver` 提供：

- `onTlsHandshakeCompleted`
- `onCertificateReloadCompleted`
- `onCertificateExpiryWarning`

Micrometer/JFR Adapter 可以消费这些事件。

TLS handshake failure 会归一化到 Observer，不需要解析日志文本。

## 9. 安全边界

mTLS 证明的是服务 Transport 身份，不等于业务授权。

~~~text
TLS/mTLS
    -> transport encryption / service identity
    -> Internal Token / application identity
    -> method/resource authorization
~~~

因此 mTLS 不替代 Peach Cloud 等业务系统自己的 Internal Token、Permission 或审计策略。

## 10. 自动化验证

当前真实网络测试覆盖：

- trusted TLS success；
- wrong CA fail；
- hostname mismatch fail；
- mTLS success；
- missing client certificate fail；
- expired certificate fail-fast；
- valid certificate reload；
- invalid reload keeps old material；
- TLS + Heartbeat + reconnect；
- plaintext Transport regression 由原有 Transport suite 持续覆盖。
