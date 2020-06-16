package tech.pegasys.teku.networking.eth2.rpc;

import static java.lang.Integer.max;
import static java.lang.Integer.min;

import com.google.common.collect.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;

public class Utils {

  public static List<List<ByteBuf>> generateTestSlices(Bytes... chunks) {
    int totalLen = Arrays.stream(chunks).mapToInt(Bytes::size).sum();
    List<List<ByteBuf>> splits = List.of(
        List.of(toByteBuf(chunks)),
        Arrays.stream(chunks).map(Utils::toByteBuf).collect(Collectors.toList()),
        Arrays.stream(chunks)
            .map(Utils::toByteBuf)
            .flatMap(b -> Utils.slice(b, 1).stream())
            .collect(Collectors.toList()),
        Arrays.stream(chunks)
            .map(Utils::toByteBuf)
            .flatMap(b -> Utils.slice(b, 2).stream())
            .collect(Collectors.toList()),
        Arrays.stream(chunks)
            .map(Utils::toByteBuf)
            .flatMap(b -> Utils.slice(b, 1, 2).stream())
            .collect(Collectors.toList()),
        Arrays.stream(chunks)
            .map(Utils::toByteBuf)
            .flatMap(b -> Utils.slice(b, -1).stream())
            .collect(Collectors.toList()),
        Arrays.stream(chunks)
            .map(Utils::toByteBuf)
            .flatMap(b -> Utils.slice(b, -2).stream())
            .collect(Collectors.toList()),
        Utils.slice(toByteBuf(chunks), totalLen / 3, 2 * totalLen / 3)
    );

    List<List<ByteBuf>> ret =
        Stream.concat(splits.stream(), addZeroLenBuffers(splits).stream())
            .collect(Collectors.toList());

    return ret;
  }


  public static List<List<ByteBuf>> addZeroLenBuffers(List<List<ByteBuf>> bufSets) {
    return bufSets.stream()
        .map(
            set ->
                set.stream()
                    .flatMap(b -> Stream.of(emptyBuf(), b.copy(), emptyBuf()))
                    .collect(Collectors.toList()))
        .collect(Collectors.toList());
  }

  public static List<ByteBuf> slice(ByteBuf src, int... pos) {
    int[] pos1 =
        Arrays.stream(pos)
            .map(i -> i >= 0 ? i : src.readableBytes() + i)
            .map(i -> max(0, min(i, src.readableBytes())))
            .toArray();
    return Streams.zip(
            IntStream.concat(IntStream.of(0), Arrays.stream(pos1)).boxed(),
            IntStream.concat(Arrays.stream(pos1), IntStream.of(src.readableBytes())).boxed(),
            Pair::of)
        .map(rng -> Pair.of(rng.getLeft(), rng.getRight() - rng.getLeft()))
        .map(il -> src.slice(il.getLeft(), il.getRight()).copy())
        // check that buffer with non-zero offset works well
        .map(bb -> Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(new byte[4]), bb).readerIndex(4))
        .collect(Collectors.toList());
  }


  public static ByteBuf emptyBuf() {
    // to avoid ByteBuf.EMPTY which always has reference count > 0
    return Unpooled.wrappedBuffer(new byte[1]).readerIndex(1);
  }

  public static ByteBuf toByteBuf(final Bytes... bytes) {
    return Unpooled.wrappedBuffer(Bytes.concatenate(bytes).toArray());
  }
}
