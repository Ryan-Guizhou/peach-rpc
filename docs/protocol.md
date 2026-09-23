# Peach RPC 协议 v1

Peach RPC v1 使用 32 字节固定大端 Header，后接 Metadata 与 Payload。

## 1. 固定 Header

| 字段 | 字节 | 作用 |
|---|---:|---|
| Magic | 2 | 固定 `0xCAFE` |
| Version | 1 | 协议版本 |
| Header Length | 1 | v1 固定 32 |
| Flags | 2 | 预留标志位 |
| Message Type | 1 | Request/Response/控制消息 |
| Codec | 1 | Codec 编号 |
| Compression | 1 | 压缩编号 |
| Status | 1 | 响应状态 |
| Request ID | 8 | 多路复用关联标识 |
| Service ID | 4 | 稳定服务编号 |
| Method ID | 4 | 稳定方法编号 |
| Metadata Length | 2 | Metadata 长度 |
| Payload Length | 4 | Payload 长度 |

Decoder 校验 Magic、Version、长度和单帧大小。

## 2. Message Type

当前协议编号：

| 类型 | ID |
|---|---:|
| REQUEST | 1 |
| RESPONSE | 2 |
| PING | 3 |
| PONG | 4 |
| GO_AWAY | 5 |
| HELLO | 6 |
| HELLO_ACK | 7 |

HELLO/HELLO_ACK 的能力模型和协商算法已经实现，但 Vert.x Transport 尚未在真实 TCP 建连流程中启用它们；该接线属于 V2-B。

## 3. Codec ID

Codec ID 是稳定线协议 ABI：

| ID | Codec |
|---:|---|
| 0 | Control Reserved |
| 1 | Fory Native |
| 2 | Fory XLang |
| 3 | Protobuf |
| 4 | Kryo |
| 5 | Hessian2 |
| 6 | JSON |

ID 0 禁止业务 Codec 使用。它专门表示 Peach RPC Core 自有控制/框架载荷。错误响应使用 ID 0 和稳定的 `RpcErrorCodec`，因此业务 Codec 即使是 Protobuf 等方法 Schema Codec，也不需要负责框架错误编码。

后续官方 Codec 必须显式分配固定 ID，不能依赖 SPI/Classpath 加载顺序。

## 4. Compression ID

| ID | Compression |
|---:|---|
| 0 | NONE |
| 1 | LZ4 |
| 2 | ZSTD |

当前数据帧仍实际使用 NONE；LZ4/ZSTD 只是协议兼容性预留，V2-B/V2-C 在完成基准后再接入。

## 5. 握手能力

`RpcConnectionCapabilities` 可以声明：

- 支持的协议版本；
- Codec 集合；
- Compression 集合；
- DEADLINE / CANCEL / STREAMING / GO_AWAY Feature；
- Max Frame Bytes。

协商规则：

1. 选择双方最高共同协议版本；
2. Codec 必须存在非空交集；
3. Compression 必须存在非空交集；
4. Feature 取交集；
5. Max Frame 取双方较小值。

没有共同协议、Codec 或 Compression 时握手失败。

## 6. 当前限制

- 真实 TCP HELLO/HELLO_ACK 尚未启用；
- CANCEL 只预留 Feature，没有传播实现；
- Streaming 未实现；
- Compression 尚未接入数据面；
- TLS/mTLS 不属于 v1 当前实现。
