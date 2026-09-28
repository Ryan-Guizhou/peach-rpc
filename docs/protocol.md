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
| Request ID | 8 | connection-local 多路复用关联标识 |
| Service ID | 4 | 稳定服务编号 |
| Method ID | 4 | 稳定方法编号 |
| Metadata Length | 2 | Metadata 长度 |
| Payload Length | 4 | Payload 长度 |

## 2. Message Type

| 类型 | ID |
|---|---:|
| REQUEST | 1 |
| RESPONSE | 2 |
| PING | 3 |
| PONG | 4 |
| GO_AWAY | 5 |
| HELLO | 6 |
| HELLO_ACK | 7 |
| CANCEL | 8 |

## 3. Codec ID

| ID | Codec |
|---:|---|
| 0 | Core / Control Reserved |
| 1 | Fory Native |
| 2 | Fory XLang |
| 3 | Protobuf |
| 4 | Kryo |
| 5 | Hessian2 |
| 6 | JSON |

错误响应使用 Codec ID 0 和 Core 自有 `RpcErrorCodec`，不依赖业务 Codec。

## 4. Compression ID

| ID | Compression |
|---:|---|
| 0 | NONE |
| 1 | LZ4 |
| 2 | ZSTD |

当前数据帧只实际启用 NONE。LZ4/ZSTD 继续保留 wire ID，待基准后再决定默认策略。

## 5. HELLO / HELLO_ACK

Vert.x Transport 已真实启用连接握手。

Client 建立 TCP 后发送 HELLO，包含：

- Protocol Version 集合；
- Codec 集合；
- Compression 集合；
- DEADLINE / CANCEL / STREAMING / GO_AWAY Feature；
- Max Frame Bytes。

Server 计算能力交集并回复 HELLO_ACK；Client 再计算相同协商结果。

没有共同 Protocol、Codec 或 Compression 时连接被拒绝。

Client 和 Server 都有独立 `handshakeTimeout`。握手超时不会进入 Active 状态。

## 6. Unary 快路径

普通 Unary REQUEST 使用 `encodeRequest`：

- Request ID 初始为 0；
- Transport 在目标 Connection Event Loop 内分配 connection-local Request ID；
- Deadline 直接编码为 `deadlineEpochMillis=<digits>\n`；
- 不构造 Metadata Map。

普通 Unary RESPONSE 使用 `encodeResponse`，当前不携带 Metadata。

控制帧和兼容场景仍可使用通用 `RpcFrame` 编解码。

## 7. Frame View

接收端使用 `RpcFrameView` 解析 Header，同时保留 backing byte[]。

Payload 由 offset/length 表示。支持 slice decode 的 Codec 可以直接消费完整帧区间，不需要 Core 再复制 payload。

## 8. CANCEL 与 Graceful Drain

V2-B.1 第一批已经把取消传播接入真实 Vert.x Transport：

```text
Consumer Future.cancel / timeout
  -> CANCEL(requestId)
  -> Provider connection-local inflight table
  -> cancel handler future
  -> interrupt virtual-thread task
```

CANCEL 只有在 HELLO/HELLO_ACK 双方协商出 `RpcFeature.CANCEL` 时发送。未知或已经完成的 Request ID 按幂等控制消息处理，不产生业务响应。

Provider 关闭流程先从 Registry 注销服务，再进入 DRAINING。Transport 对已有连接发送 `GO_AWAY(UNAVAILABLE)`，拒绝新的 REQUEST，但允许已接收请求完成；inflight 清零或达到 drain timeout 后关闭连接。协议错误使用其他状态的 GO_AWAY，仍属于 fatal connection error，不进入 graceful drain。

## 9. 当前限制

- Streaming 未实现；
- Compression 尚未进入数据面；
- TLS/mTLS 未实现；
- Transport/Core 仍以 byte[] 完整帧为 API 边界。
