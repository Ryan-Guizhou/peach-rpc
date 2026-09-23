package io.peach.rpc.codec;

/** Peach RPC 官方 Codec 线协议编号。 */
public final class RpcCodecIds {

    /** 连接控制帧保留编号，不允许业务 Codec 使用。 */
    public static final byte CONTROL = 0;
    /** Apache Fory Java Native。 */
    public static final byte FORY_NATIVE = 1;
    /** Apache Fory XLang。 */
    public static final byte FORY_XLANG = 2;
    /** Protocol Buffers。 */
    public static final byte PROTOBUF = 3;
    /** Kryo。 */
    public static final byte KRYO = 4;
    /** Hessian2。 */
    public static final byte HESSIAN2 = 5;
    /** JSON 调试与互操作 Codec。 */
    public static final byte JSON = 6;

    private RpcCodecIds() {
    }

    /**
     * 校验业务 Codec 编号。
     *
     * <p>0 为控制帧保留编号，1~127 为官方与预留扩展区，128~255 为用户自定义区。
     *
     * @param code Codec 编号
     */
    public static void validateApplicationCodec(byte code) {
        if (Byte.toUnsignedInt(code) == Byte.toUnsignedInt(CONTROL)) {
            throw new IllegalArgumentException("Codec id 0 is reserved for protocol control frames");
        }
    }
}
