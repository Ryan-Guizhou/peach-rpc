package io.peach.rpc.codec;

import io.peach.rpc.spi.SPI;

/** RPC 消息体编解码扩展点。 */
@SPI("fory")
public interface RpcCodec {

    /**
     * 返回线协议中的 Codec 编号。
     *
     * @return Codec 编号
     */
    byte code();

    /**
     * 序列化 Java 对象。
     *
     * @param value 待序列化对象
     * @return 序列化结果
     */
    byte[] encode(Object value);

    /**
     * 反序列化消息体。
     *
     * @param bytes 消息体
     * @param type 目标类型
     * @param <T> 目标类型
     * @return 反序列化对象
     */
    <T> T decode(byte[] bytes, Class<T> type);
}
