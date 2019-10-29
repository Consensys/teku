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

import java.util.Random;

/** A {@link BytesValue} that is guaranteed to contain exactly 16 bytes. */
public interface Bytes16 extends BytesValue {
  int SIZE = 16;
  Bytes16 ZERO = wrap(new byte[16]);

  /**
   * Wraps the provided byte array, which must be of length 16, as a {@link Bytes16}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @return A {@link Bytes16} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 16}.
   */
  static Bytes16 wrap(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return wrap(bytes, 0);
  }

  /**
   * Wraps a slice/sub-part of the provided array as a {@link Bytes16}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value[i]}.
   * @return A {@link Bytes16} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 16} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.length &gt; 0 && offset >=
   *     value.length)}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 16 &gt; value.length}.
   */
  static Bytes16 wrap(byte[] bytes, int offset) {
    return new ArrayWrappingBytes16(bytes, offset);
  }

  /**
   * Wraps a slice/sub-part of the provided value as a {@link Bytes16}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value.get(i)}.
   * @return A {@link Bytes16} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 16} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.size() &gt; 0 && offset >=
   *     value.size())}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 16 &gt; value.size()}.
   */
  static Bytes16 wrap(BytesValue bytes, int offset) {
    BytesValue slice = bytes.slice(offset, Bytes16.SIZE);
    return slice instanceof Bytes16 ? (Bytes16) slice : new WrappingBytes16(slice);
  }

  /**
   * Left pad a {@link BytesValue} with zero bytes to create a {@link Bytes16}
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes16} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 16}.
   */
  static Bytes16 leftPad(BytesValue value) {
    checkArgument(
        value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE, value.size());

    MutableBytes16 bytes = MutableBytes16.create();
    value.copyTo(bytes, SIZE - value.size());
    return bytes;
  }

  /**
   * Right pad a {@link BytesValue} with zero bytes to create a {@link Bytes16}
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes16} that exposes the right-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 16}.
   */
  static Bytes16 rightPad(BytesValue value) {
    checkArgument(
        value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE, value.size());

    MutableBytes16 bytes = MutableBytes16.create();
    value.copyTo(bytes, 0);
    return bytes;
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes16}.
   *
   * <p>This method is lenient in that {@code str} may of an odd length, in which case it will
   * behave exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 16 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation or contains more than 16 bytes.
   */
  static Bytes16 fromHexStringLenient(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, true));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes16}.
   *
   * <p>This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 16 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length, or contains more than 16 bytes.
   */
  static Bytes16 fromHexString(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, false));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes16}.
   *
   * <p>This method is extra strict in that {@code str} must of an even length and the provided
   * representation must have exactly 16 bytes.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length or does not contain exactly 16 bytes.
   */
  static Bytes16 fromHexStringStrict(String str) {
    return wrap(BytesValues.fromRawHexString(str, -1, false));
  }

  /**
   * Constructs a randomly generated value.
   *
   * @param rnd random number generator.
   * @return random value.
   */
  static Bytes16 random(Random rnd) {
    byte[] randomBytes = new byte[SIZE];
    rnd.nextBytes(randomBytes);
    return wrap(randomBytes);
  }

  @Override
  default int size() {
    return SIZE;
  }

  @Override
  Bytes16 copy();

  @Override
  MutableBytes16 mutableCopy();
}
