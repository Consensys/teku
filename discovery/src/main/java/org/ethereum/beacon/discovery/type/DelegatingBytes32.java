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

package org.ethereum.beacon.discovery.type;

public class DelegatingBytes32 extends BaseDelegatingBytesValue<BytesValue> implements BytesValue {
  protected DelegatingBytes32(BytesValue wrapped) {
    super(wrapped);
  }

  @Override
  public BytesValue copy() {
    return wrapped.copy();
  }

  @Override
  public MutableBytesValue mutableCopy() {
    return wrapped.mutableCopy();
  }
}
