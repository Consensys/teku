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

package tech.pegasys.artemis.datastructures.BeaconChainState;

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

public class ValidatorRegistryDeltaBlock {

  private Hash latest_registry_delta_root;
  private int validator_index;
  private UInt384 pubkey;
  private UInt64 flag;

  public ValidatorRegistryDeltaBlock(Hash latest_registry_delta_root, int validator_index,
                                     UInt384 pubkey, UInt64 flag) {
    this.latest_registry_delta_root = latest_registry_delta_root;
    this.validator_index = validator_index;
    this.pubkey = pubkey;
    this.flag = flag;
  }
}
