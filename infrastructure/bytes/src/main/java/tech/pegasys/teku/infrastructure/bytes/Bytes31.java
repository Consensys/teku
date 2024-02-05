/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class Bytes31 {
  public static final int SIZE = 31;
  public static final Bytes31 ZERO = new Bytes31(Bytes.wrap(new byte[SIZE]));

  private final Bytes bytes;

  public Bytes31(Bytes bytes) {
    checkArgument(
        bytes.size() == SIZE, "Bytes31 should be 31 bytes, but was %s bytes.", bytes.size());
    this.bytes = bytes;
  }

  public static Bytes31 fromHexString(String value) {
    return new Bytes31(Bytes.fromHexString(value));
  }

  public String toHexString() {
    return bytes.toHexString();
  }

  /**
   * Left pad a {@link Bytes} value with zero bytes to create a {@link Bytes31}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes31} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() > 31}.
   */
  public static Bytes31 leftPad(Bytes value) {
    checkNotNull(value);
    if (value instanceof Bytes31) {
      return (Bytes31) value;
    }
    checkArgument(value.size() <= SIZE, "Expected at most %s bytes but got %s", SIZE, value.size());
    MutableBytes result = MutableBytes.create(SIZE);
    value.copyTo(result, SIZE - value.size());
    return new Bytes31(result);
  }

  /**
   * Right pad a {@link Bytes} value with zero bytes to create a {@link Bytes31}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes31} that exposes the right-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() > 31}.
   */
  public static Bytes31 rightPad(Bytes value) {
    checkNotNull(value);
    if (value instanceof Bytes31) {
      return (Bytes31) value;
    }
    checkArgument(value.size() <= SIZE, "Expected at most %s bytes but got %s", SIZE, value.size());
    MutableBytes result = MutableBytes.create(SIZE);
    value.copyTo(result, 0);
    return new Bytes31(result);
  }

  public Bytes getWrappedBytes() {
    return bytes;
  }

  public Bytes31 copy() {
    return new Bytes31(bytes.copy());
  }

  public BigInteger toUnsignedBigInteger() {
    return bytes.toUnsignedBigInteger();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Bytes31 bytes31 = (Bytes31) o;
    return bytes.equals(bytes31.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes);
  }

  public String toUnprefixedHexString() {
    return bytes.toUnprefixedHexString();
  }

  @Override
  public String toString() {
    return bytes.toString();
  }
}
