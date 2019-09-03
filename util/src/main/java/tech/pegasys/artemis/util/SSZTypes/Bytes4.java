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

import org.apache.tuweni.bytes.Bytes;

public class Bytes4 {

  private Bytes bytes;

  public Bytes4(Bytes bytes) {
    checkArgument(bytes.size() == 4, "Bytes4 should be 4 bytes, but was %s bytes.", bytes.size());
    this.bytes = bytes;
  }

  public Bytes getWrappedBytes() {
    return bytes;
  }
}
