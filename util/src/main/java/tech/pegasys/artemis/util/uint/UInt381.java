package tech.pegasys.artemis.util.uint;

public class UInt381 {
  /** The value 0. */
  public static final UInt381 ZERO = new UInt381(0);
  /** The value 1. */
  public static final UInt381 ONE = new UInt381(1);

  private final long value;

  private UInt381(long value) {
    this.value = value;
  }

  public UInt381(UInt381 uint) {
    this.value = uint.getValue();
  }

  public static UInt381 valueOf(long l) {
    return
  }

  public long getValue() {
    return value;
  }

}
