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

package net.consensys.artemis.util.bytes;

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
