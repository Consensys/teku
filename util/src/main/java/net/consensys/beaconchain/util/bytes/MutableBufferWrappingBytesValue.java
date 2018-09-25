package net.consensys.beaconchain.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import io.vertx.core.buffer.Buffer;

class MutableBufferWrappingBytesValue extends AbstractBytesValue implements MutableBytesValue {

  private final Buffer buffer;
  private final int offset;
  private final int size;

  MutableBufferWrappingBytesValue(Buffer buffer, int offset, int size) {
    checkArgument(size >= 0, "Invalid negative length provided");
    checkElementIndex(offset, buffer.length());
    checkArgument(offset + size <= buffer.length(),
        "Provided length %s is too big: the buffer has size %s and has only %s bytes from %s", size,
        buffer.length(), buffer.length() - offset, offset);

    this.buffer = buffer;
    this.offset = offset;
    this.size = size;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public byte get(int i) {
    return buffer.getByte(offset + i);
  }

  @Override
  public void set(int i, byte b) {
    buffer.setByte(offset + i, b);
  }

  @Override
  public MutableBytesValue mutableSlice(int index, int length) {
    if (index == 0 && length == size()) {
      return this;
    }
    if (length == 0) {
      return MutableBytesValue.EMPTY;
    }

    checkElementIndex(index, size());
    checkArgument(index + length <= size(),
        "Provided length %s is too big: the value has size %s and has only %s bytes from %s",
        length, size(), size() - index, index);

    return new MutableBufferWrappingBytesValue(buffer, offset + index, length);
  }

  @Override
  public BytesValue slice(int index, int length) {
    return mutableSlice(index, length);
  }
}
