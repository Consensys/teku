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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;

public class ValidatorRegistryDeltaBlock {

  private Bytes32 latest_registry_delta_root;
  private int validator_index;
  private Bytes48 pubkey;
  private UnsignedLong flag;

  public ValidatorRegistryDeltaBlock(
      Bytes32 latest_registry_delta_root, int validator_index, Bytes48 pubkey, UnsignedLong flag) {
    this.latest_registry_delta_root = latest_registry_delta_root;
    this.validator_index = validator_index;
    this.pubkey = pubkey;
    this.flag = flag;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getLatest_registry_delta_root() {
    return latest_registry_delta_root;
  }

  public void setLatest_registry_delta_root(Bytes32 latest_registry_delta_root) {
    this.latest_registry_delta_root = latest_registry_delta_root;
  }

  public int getValidator_index() {
    return validator_index;
  }

  public void setValidator_index(int validator_index) {
    this.validator_index = validator_index;
  }

  public Bytes48 getPubkey() {
    return pubkey;
  }

  public void setPubkey(Bytes48 pubkey) {
    this.pubkey = pubkey;
  }

  public UnsignedLong getFlag() {
    return flag;
  }

  public void setFlag(UnsignedLong flag) {
    this.flag = flag;
  }
}
