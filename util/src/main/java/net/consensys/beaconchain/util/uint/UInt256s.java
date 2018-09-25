package net.consensys.beaconchain.util.uint;

/** Static utility methods on UInt256 values. */
public class UInt256s {

  /**
   * Returns the maximum of 2 UInt256 values.
   *
   * @param v1 The first value.
   * @param v2 The second value.
   * @return The maximum of {@code v1} and {@code v2}.
   * @param <T> The concrete type of the two values.
   */
  public static <T extends UInt256Value<T>> T max(T v1, T v2) {
    return (v1.compareTo(v2)) >= 0 ? v1 : v2;
  }

  /**
   * Returns the minimum of 2 UInt256 values.
   *
   * @param v1 The first value.
   * @param v2 The second value.
   * @return The minimum of {@code v1} and {@code v2}.
   * @param <T> The concrete type of the two values.
   */
  public static <T extends UInt256Value<T>> T min(T v1, T v2) {
    return (v1.compareTo(v2)) < 0 ? v1 : v2;
  }
}
