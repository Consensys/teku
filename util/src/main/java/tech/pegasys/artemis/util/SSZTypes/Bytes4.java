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

package tech.pegasys.artemis.util.SSZTypes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class Bytes4 {

  private Bytes bytes;

  public Bytes4(Bytes bytes) {
    checkArgument(bytes.size() == 4, "Bytes4 should be 4 bytes, but was %s bytes.", bytes.size());
    this.bytes = bytes;
  }

  /**
   * Left pad a {@link Bytes} value with zero bytes to create a {@link Bytes4}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes4} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 4}.
   */
  public static Bytes4 leftPad(Bytes value) {
    checkNotNull(value);
    if (value instanceof Bytes4) {
      return (Bytes4) value;
    }
    checkArgument(value.size() <= 4, "Expected at most %s bytes but got %s", 4, value.size());
    MutableBytes result = MutableBytes.create(4);
    value.copyTo(result, 4 - value.size());
    return new Bytes4(result);
  }

  /**
   * Right pad a {@link Bytes} value with zero bytes to create a {@link Bytes4}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes4} that exposes the right-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 4}.
   */
  public static Bytes4 rightPad(Bytes value) {
    checkNotNull(value);
    if (value instanceof Bytes4) {
      return (Bytes4) value;
    }
    checkArgument(value.size() <= 4, "Expected at most %s bytes but got %s", 4, value.size());
    MutableBytes result = MutableBytes.create(4);
    value.copyTo(result, 0);
    return new Bytes4(result);
  }

  public Bytes getWrappedBytes() {
    return bytes;
  }
}
