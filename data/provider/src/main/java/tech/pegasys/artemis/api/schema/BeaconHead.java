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

package tech.pegasys.artemis.api.schema;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class BeaconHead {
  public final UnsignedLong slot;
  public final Bytes32 block_root;
  public final Bytes32 state_root;

  public BeaconHead(UnsignedLong slot, Bytes32 block_root, Bytes32 state_root) {
    this.slot = slot;
    this.block_root = block_root;
    this.state_root = state_root;
  }
}
