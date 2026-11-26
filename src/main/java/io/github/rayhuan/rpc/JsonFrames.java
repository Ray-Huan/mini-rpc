package io.github.rayhuan.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * Length-prefixed JSON frames.
 *
 * <pre>+--------+------------------------+
 * | len(4) | utf-8 json payload      |
 * +--------+------------------------+
 * </pre>
 *
 * JSON instead of protobuf: the wire stays readable with {@code nc}, which is the first
 * thing you want when a call fails and nobody remembers what was actually sent.
 */
public final class JsonFrames {

    public static final int MAX_FRAME = 8 * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonFrames() {
    }

    /** Splits the stream into bare payload buffers. */
    public static LengthFieldBasedFrameDecoder splitter() {
        return new LengthFieldBasedFrameDecoder(MAX_FRAME, 0, 4, 0, 4);
    }

    /** An inbound decoder gives a typed object back. */
    public static <T> MessageToMessageDecoder<ByteBuf> decoder(Class<T> type) {
        return new MessageToMessageDecoder<>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) throws Exception {
                byte[] body = new byte[frame.readableBytes()];
                frame.readBytes(body);
                out.add(MAPPER.readValue(body, type));
            }
        };
    }

    /** Everything written from the handler side is serialised here. */
    public static MessageToMessageEncoder<Object> encoder() {
        return new MessageToMessageEncoder<>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                byte[] body = MAPPER.writeValueAsBytes(msg);
                ByteBuf buf = ctx.alloc().buffer(4 + body.length);
                buf.writeInt(body.length);
                buf.writeBytes(body);
                out.add(buf);
            }
        };
    }

    public static byte[] toJson(Object value) throws Exception {
        return MAPPER.writeValueAsBytes(value);
    }

    public static <T> T fromJson(byte[] body, Class<T> type) throws Exception {
        return MAPPER.readValue(body, type);
    }

    public static ByteBuf frame(ChannelHandlerContext ctx, byte[] body) {
        ByteBuf buf = ctx.alloc().buffer(4 + body.length);
        buf.writeInt(body.length);
        buf.writeBytes(body);
        return buf;
    }

    static ByteBuf frameForTest(byte[] body) {
        ByteBuf buf = Unpooled.buffer(4 + body.length);
        buf.writeInt(body.length);
        buf.writeBytes(body);
        return buf;
    }
}
