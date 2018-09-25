package net.consensys.beaconchain.ethereum.rlp;

import net.consensys.beaconchain.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class RLPBench {
  private static Object generate(int depth, int width, int size) {
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) ((100 + i) * i);
    }
    return generateAndRecurse(BytesValue.wrap(bytes), depth, width);
  }

  private static Object generateAndRecurse(BytesValue value, int depth, int width) {
    if (depth == 0) {
      return value;
    }

    List<Object> l = new ArrayList<>(width);
    for (int i = 0; i < width; i++) {
      l.add(i % 3 == 0 ? value : generateAndRecurse(value, depth - 1, width));
    }
    return l;
  }

  @Param({"1", "3", "8"})
  public int depth;

  @Param({"4", "8"})
  public int width;

  @Param({"4", "100"})
  public int size;

  volatile Object toEncode;
  volatile BytesValue toDecode;

  @Setup(Level.Trial)
  public void prepare() {
    toEncode = generate(depth, width, size);
    toDecode = RLP.encode(toEncode);
  }

  @Benchmark
  public BytesValue benchmarkEncoding() {
    return RLP.encode(toEncode);
  }

  @Benchmark
  public Object benchmarkDecoding() {
    return RLP.decode(toDecode);
  }
}
