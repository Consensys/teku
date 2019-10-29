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

package org.ethereum.beacon.core.state;

import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * A diff between two changes of the validator registry.
 *
 * @see BeaconState
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#validatorregistrydeltablock>ValidatorRegistryDeltaBlock
 *     in the spec</a>
 */
public class ValidatorRegistryDeltaBlock {

  /** A hash of previous registry delta block. */
  private final Hash32 latestRegistryDeltaRoot;
  /** An index of validator that has been changed. */
  private final ValidatorIndex validatorIndex;
  /** BLS public key of the validator. */
  private final BLSPubkey pubKey;

  private final SlotNumber slot;
  /** A code denoting an action applied to the validator. */
  private final UInt64 flag;

  public ValidatorRegistryDeltaBlock(
      Hash32 latestRegistryDeltaRoot,
      ValidatorIndex validatorIndex,
      BLSPubkey pubKey,
      SlotNumber slot,
      UInt64 flag) {
    this.latestRegistryDeltaRoot = latestRegistryDeltaRoot;
    this.validatorIndex = validatorIndex;
    this.pubKey = pubKey;
    this.slot = slot;
    this.flag = flag;
  }

  public Hash32 getLatestRegistryDeltaRoot() {
    return latestRegistryDeltaRoot;
  }

  public ValidatorIndex getValidatorIndex() {
    return validatorIndex;
  }

  public BLSPubkey getPubKey() {
    return pubKey;
  }

  public SlotNumber getSlot() {
    return slot;
  }

  public UInt64 getFlag() {
    return flag;
  }
}
