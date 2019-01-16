/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;

import io.vertx.core.buffer.Buffer;
import java.security.MessageDigest;
import java.util.Arrays;

/** An implementation of {@link BytesValue} backed by a byte array ({@code byte[]}). */
class ArrayWrappingBytesValue extends AbstractBytesValue {

  protected final byte[] bytes;
  protected final int offset;
  protected final int length;

  ArrayWrappingBytesValue(byte[] bytes) {
    this(bytes, 0, bytes.length);
  }

  ArrayWrappingBytesValue(byte[] bytes, int offset, int length) {
    checkNotNull(bytes, "Invalid 'null' byte array provided");
    checkArgument(length >= 0, "Invalid negative length provided");
    if (bytes.length > 0) {
      checkElementIndex(offset, bytes.length);
    }
    checkArgument(
        offset + length <= bytes.length,
        "Provided length %s is too big: the value has only %s bytes from offset %s",
        length,
        bytes.length - offset,
        offset);

    this.bytes = bytes;
    this.offset = offset;
    this.length = length;
  }

  private byte[] extractOrGetArrayUnsafe() {
    if (offset == 0 && length == bytes.length) {
      return bytes;
    }

    return Arrays.copyOfRange(bytes, offset, offset + length);
  }

  @Override
  public int size() {
    return length;
  }

  @Override
  public byte get(int i) {
    // Check bounds because while the array access would throw, the error message would be confusing
    // for the caller.
    checkElementIndex(i, size());
    return bytes[offset + i];
  }

  @Override
  public BytesValue slice(int index, int length) {
    if (index == 0 && length == size()) {
      return this;
    }
    if (length == 0) {
      return BytesValue.EMPTY;
    }

    checkElementIndex(index, size());
    checkArgument(
        index + length <= size(),
        "Provided length %s is too big: the value has size %s and has only %s bytes from %s",
        length,
        size(),
        size() - index,
        index);

    return length == Bytes32.SIZE
        ? new ArrayWrappingBytes32(bytes, offset + index)
        : new ArrayWrappingBytesValue(bytes, offset + index, length);
  }

  byte[] arrayCopy() {
    return Arrays.copyOfRange(bytes, offset, offset + length);
  }

  @Override
  public BytesValue copy() {
    // Because MutableArrayWrappingBytesValue overrides this, we know we are immutable. We may
    // retain more than necessary however.
    if (offset == 0 && length == bytes.length) {
      return this;
    }

    return new ArrayWrappingBytesValue(arrayCopy());
  }

  @Override
  public MutableBytesValue mutableCopy() {
    return new MutableArrayWrappingBytesValue(arrayCopy());
  }

  @Override
  public int commonPrefixLength(BytesValue other) {
    if (!(other instanceof ArrayWrappingBytesValue)) {
      return super.commonPrefixLength(other);
    }
    ArrayWrappingBytesValue o = (ArrayWrappingBytesValue) other;
    int i = 0;
    while (i < length && i < o.length && bytes[offset + i] == o.bytes[o.offset + i]) {
      i++;
    }
    return i;
  }

  @Override
  public void update(MessageDigest digest) {
    digest.update(bytes, offset, length);
  }

  @Override
  public void copyTo(MutableBytesValue dest) {
    checkArgument(
        dest.size() == size(),
        "Cannot copy %s bytes to destination of non-equal size %s",
        size(),
        dest.size());

    copyTo(dest, 0);
  }

  @Override
  public void copyTo(MutableBytesValue destination, int destinationOffset) {
    if (!(destination instanceof MutableArrayWrappingBytesValue)) {
      super.copyTo(destination, destinationOffset);
      return;
    }

    // Special casing an empty source or the following checks might throw (even though we have
    // nothing to copy anyway) and this gets inconvenient for generic methods using copyTo() as
    // they may have to special case empty values because of this. As an example,
    // concatenate(EMPTY, EMPTY) would need to be special cased without this.
    if (size() == 0) {
      return;
    }

    checkElementIndex(destinationOffset, destination.size());
    checkArgument(
        destination.size() - destinationOffset >= size(),
        "Cannot copy %s bytes, destination has only %s bytes from index %s",
        size(),
        destination.size() - destinationOffset,
        destinationOffset);

    MutableArrayWrappingBytesValue d = (MutableArrayWrappingBytesValue) destination;
    System.arraycopy(bytes, offset, d.bytes, d.offset + destinationOffset, size());
  }

  @Override
  public void appendTo(Buffer buffer) {
    buffer.appendBytes(bytes, offset, length);
  }

  @Override
  public byte[] getArrayUnsafe() {
    return extractOrGetArrayUnsafe();
  }
}
