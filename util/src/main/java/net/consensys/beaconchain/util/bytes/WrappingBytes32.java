package net.consensys.beaconchain.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A simple class to wrap another {@link BytesValue} of exactly 32 bytes as a {@link Bytes32}.
 */
class WrappingBytes32 extends AbstractBytesValue implements Bytes32 {

  private final BytesValue value;

  WrappingBytes32(BytesValue value) {
    checkArgument(value.size() == SIZE, "Expected value to be %s bytes, but is %s bytes", SIZE,
        value.size());
    this.value = value;
  }

  @Override
  public byte get(int i) {
    return value.get(i);
  }

  @Override
  public BytesValue slice(int index, int length) {
    return value.slice(index, length);
  }

  @Override
  public MutableBytes32 mutableCopy() {
    MutableBytes32 copy = MutableBytes32.create();
    value.copyTo(copy);
    return copy;
  }

  @Override
  public Bytes32 copy() {
    return mutableCopy();
  }

  @Override
  public byte[] getArrayUnsafe() {
    return value.getArrayUnsafe();
  }

  @Override
  public int size() {
    return value.size();
  }
}
