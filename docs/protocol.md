# Peach RPC 协议 v1

Peach RPC v1 使用 32 字节固定大端 Header，后接 Metadata 与 Payload。

| 字段 | 字节 | 作用 |
|---|---:|---|
| Magic | 2 | 固定 `0xCAFE` |
| Version | 1 | 协议版本 |
| Header Length | 1 | v1 固定 32 |
| Flags | 2 | 预留标志位 |
| Message Type | 1 | Request/Response/控制消息 |
| Codec | 1 | Codec 编号 |
| Compression | 1 | 预留压缩编号 |
| Status | 1 | 响应状态 |
| Request ID | 8 | 多路复用关联标识 |
| Service ID | 4 | 稳定服务编号 |
| Method ID | 4 | 稳定方法编号 |
| Metadata Length | 2 | Metadata 长度 |
| Payload Length | 4 | Payload 长度 |

Decoder 会校验 Magic、Version、长度和单帧大小。v0.1 尚未正式启用 HELLO 协商、压缩、Streaming、Cancel 与 GO_AWAY。
