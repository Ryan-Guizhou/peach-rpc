package io.peach.rpc.codec;

/** 已在服务方法粒度预绑定的 Codec 热路径接口。 */
public interface RpcMethodCodec {

    /**
     * 返回线协议 Codec 编号。
     *
     * @return Codec 编号
     */
    byte codecId();

    /**
     * 编码调用参数。
     *
     * @param arguments 参数
     * @return 编码结果
     */
    byte[] encodeArguments(Object[] arguments);

    /**
     * 解码调用参数。
     *
     * @param payload 编码载荷
     * @return 参数
     */
    Object[] decodeArguments(byte[] payload);

    /**
     * 编码成功返回值。
     *
     * @param value 返回值
     * @return 编码结果
     */
    byte[] encodeResult(Object value);

    /**
     * 解码成功返回值。
     *
     * @param payload 编码载荷
     * @return 返回值
     */
    Object decodeResult(byte[] payload);
}
