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

/**
 * An implementation of {@link Bytes48} backed by a byte array ({@code byte[]}).
 */
class ArrayWrappingBytes48 extends ArrayWrappingBytesValue implements Bytes48 {

  ArrayWrappingBytes48(byte[] bytes) {
    this(checkLength(bytes), 0);
  }

  ArrayWrappingBytes48(byte[] bytes, int offset) {
    super(checkLength(bytes, offset), offset, SIZE);
  }

  // Ensures a proper error message.
  private static byte[] checkLength(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return bytes;
  }

  // Ensures a proper error message.
  private static byte[] checkLength(byte[] bytes, int offset) {
    checkArgument(bytes.length - offset >= SIZE,
        "Expected at least %s bytes from offset %s but got only %s", SIZE, offset,
        bytes.length - offset);
    return bytes;
  }

  @Override
  public Bytes48 copy() {
    // Because MutableArrayWrappingBytesValue overrides this, we know we are immutable. We may
    // retain more than necessary however.
    if (offset == 0 && length == bytes.length)
      return this;

    return new ArrayWrappingBytes48(arrayCopy());
  }

  @Override
  public MutableBytes48 mutableCopy() {
    return new MutableArrayWrappingBytes48(arrayCopy());
  }
}
