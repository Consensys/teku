/*
 * Copyright 2018 ConsenSys AG.
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

import java.security.MessageDigest;

/**
 * A mutable {@link Bytes32}, that is a mutable {@link BytesValue} of exactly 32 bytes.
 *
 * @see Bytes32s for static methods to create and work with {@link MutableBytes32}.
 */
public interface MutableBytes32 extends MutableBytesValue, Bytes32 {

  /**
   * Wraps a 32 bytes array as a mutable 32 bytes value.
   *
   * <p>
   * This method behave exactly as {@link Bytes32#wrap(byte[])} except that the result is a mutable.
   *
   * @param value The value to wrap.
   * @return A {@link MutableBytes32} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 32}.
   */
  static MutableBytes32 wrap(byte[] value) {
    return new MutableArrayWrappingBytes32(value);
  }

  /**
   * Creates a new mutable 32 bytes value.
   *
   * @return A newly allocated {@link MutableBytesValue}.
   */
  static MutableBytes32 create() {
    return new MutableArrayWrappingBytes32(new byte[SIZE]);
  }

  /**
   * Wraps an existing {@link MutableBytesValue} of size 32 as a mutable 32 bytes value.
   *
   * <p>
   * This method does no copy the provided bytes and so any mutation on {@code value} will also be
   * reflected in the value returned by this method. If a copy is desirable, this can be simply
   * achieved with calling {@link BytesValue#copyTo(MutableBytesValue)} with a newly created
   * {@link MutableBytes32} as destination to the copy.
   *
   * @param value The value to wrap.
   * @return A {@link MutableBytes32} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.size() != 32}.
   */
  static MutableBytes32 wrap(MutableBytesValue value) {
    checkArgument(value.size() == SIZE, "Expected %s bytes but got %s", SIZE, value.size());
    return new MutableBytes32() {
      @Override
      public void set(int i, byte b) {
        value.set(i, b);
      }

      @Override
      public MutableBytesValue mutableSlice(int i, int length) {
        return value.mutableSlice(i, length);
      }

      @Override
      public byte get(int i) {
        return value.get(i);
      }

      @Override
      public BytesValue slice(int index) {
        return value.slice(index);
      }

      @Override
      public BytesValue slice(int index, int length) {
        return value.slice(index, length);
      }

      @Override
      public Bytes32 copy() {
        return Bytes32.wrap(value.extractArray());
      }

      @Override
      public MutableBytes32 mutableCopy() {
        return MutableBytes32.wrap(value.extractArray());
      }

      @Override
      public void copyTo(MutableBytesValue destination) {
        value.copyTo(destination);
      }

      @Override
      public void copyTo(MutableBytesValue destination, int destinationOffset) {
        value.copyTo(destination, destinationOffset);
      }

      @Override
      public int commonPrefixLength(BytesValue other) {
        return value.commonPrefixLength(other);
      }

      @Override
      public BytesValue commonPrefix(BytesValue other) {
        return value.commonPrefix(other);
      }

      @Override
      public void update(MessageDigest digest) {
        value.update(digest);
      }

      @Override
      public boolean isZero() {
        return value.isZero();
      }

      @Override
      public boolean equals(Object other) {
        return value.equals(other);
      }

      @Override
      public int hashCode() {
        return value.hashCode();
      }

      @Override
      public String toString() {
        return value.toString();
      }
    };
  }
}
