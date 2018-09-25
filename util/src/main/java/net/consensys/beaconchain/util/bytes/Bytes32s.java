package net.consensys.beaconchain.util.bytes;

/** Static utility methods to work with {@link Bytes32}. */
public abstract class Bytes32s {
  private Bytes32s() {}

  public static void and(Bytes32 v1, Bytes32 v2, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) & v2.get(i)));
    }
  }

  public static Bytes32 and(Bytes32 v1, Bytes32 v2) {
    MutableBytes32 mb32 = MutableBytes32.create();
    and(v1, v2, mb32);
    return mb32;
  }

  public static void or(Bytes32 v1, Bytes32 v2, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) | v2.get(i)));
    }
  }

  public static Bytes32 or(Bytes32 v1, Bytes32 v2) {
    MutableBytes32 mb32 = MutableBytes32.create();
    or(v1, v2, mb32);
    return mb32;
  }

  public static void xor(Bytes32 v1, Bytes32 v2, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) ^ v2.get(i)));
    }
  }

  public static Bytes32 xor(Bytes32 v1, Bytes32 v2) {
    MutableBytes32 mb32 = MutableBytes32.create();
    xor(v1, v2, mb32);
    return mb32;
  }

  public static void not(Bytes32 v, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (~v.get(i)));
    }
  }

  public static Bytes32 not(Bytes32 v) {
    MutableBytes32 mb32 = MutableBytes32.create();
    not(v, mb32);
    return mb32;
  }

  public static String unprefixedHexString(Bytes32 v) {
    return v.toString().substring(2);
  }
}
