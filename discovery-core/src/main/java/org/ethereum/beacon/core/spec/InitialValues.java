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

package org.ethereum.beacon.core.spec;

import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Initial values.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#initial-values">Initial
 *     values</a> in the spec.
 */
public interface InitialValues {

  SlotNumber GENESIS_SLOT = SlotNumber.ZERO;
  EpochNumber GENESIS_EPOCH = EpochNumber.ZERO;
  UInt64 BLS_WITHDRAWAL_PREFIX = UInt64.ZERO;

  /* Values defined in the spec. */

  default SlotNumber getGenesisSlot() {
    return GENESIS_SLOT;
  }

  default EpochNumber getGenesisEpoch() {
    return GENESIS_EPOCH;
  }

  default UInt64 getBlsWithdrawalPrefix() {
    return BLS_WITHDRAWAL_PREFIX;
  }
}
