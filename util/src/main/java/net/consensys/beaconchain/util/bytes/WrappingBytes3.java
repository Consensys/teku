package net.consensys.beaconchain.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A simple class to wrap another {@link BytesValue} of exactly 3 bytes as a {@link Bytes3}.
 */
class WrappingBytes3 extends AbstractBytesValue implements Bytes3 {

  private final BytesValue value;

  WrappingBytes3(BytesValue value) {
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
  public MutableBytes3 mutableCopy() {
    MutableBytes3 copy = MutableBytes3.create();
    value.copyTo(copy);
    return copy;
  }

  @Override
  public Bytes3 copy() {
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
