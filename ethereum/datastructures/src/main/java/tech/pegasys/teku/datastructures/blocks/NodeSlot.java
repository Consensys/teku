/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.primitives.UnsignedLong;

public class NodeSlot {
  private volatile UnsignedLong value;

  public NodeSlot(UnsignedLong value) {
    this.value = value;
  }

  public UnsignedLong getValue() {
    return value;
  }

  public UnsignedLong inc() {
    value = value.plus(UnsignedLong.ONE);
    return value;
  }

  public void setValue(UnsignedLong value) {
    this.value = value;
  }

  public long longValue() {
    return this.value.longValue();
  }
}
