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
 * An implementation of {@link Bytes1} backed by a byte array ({@code byte[]}).
 */
class ArrayWrappingBytes1 extends ArrayWrappingBytesValue implements Bytes1 {

  ArrayWrappingBytes1(byte[] bytes) {
    super(checkLength(bytes), 0, SIZE);
  }

  // Ensures a proper error message.
  private static byte[] checkLength(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return bytes;
  }

  @Override
  public Bytes1 copy() {
    // Because MutableArrayWrappingBytesValue overrides this, we know we are immutable. We may
    // retain more than necessary however.
    if (offset == 0 && length == bytes.length)
      return this;

    return new ArrayWrappingBytes1(arrayCopy());
  }

  @Override
  public MutableBytes1 mutableCopy() {
    return new MutableArrayWrappingBytes1(arrayCopy());
  }
}
