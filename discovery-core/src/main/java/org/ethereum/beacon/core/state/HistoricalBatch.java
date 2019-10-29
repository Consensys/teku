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

import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.ReadVector;

/**
 * A batch of historical data.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#historicalbatch">HistoricalBatch</a>
 *     in the spec.
 */
@SSZSerializable
public class HistoricalBatch {

  /** Block roots. */
  @SSZ(vectorLengthVar = "spec.SLOTS_PER_HISTORICAL_ROOT")
  private final ReadVector<SlotNumber, Hash32> blockRoots;
  /** State roots. */
  @SSZ(vectorLengthVar = "spec.SLOTS_PER_HISTORICAL_ROOT")
  private final ReadVector<SlotNumber, Hash32> stateRoots;

  public HistoricalBatch(
      ReadVector<SlotNumber, Hash32> blockRoots, ReadVector<SlotNumber, Hash32> stateRoots) {
    this.blockRoots = blockRoots;
    this.stateRoots = stateRoots;
  }

  public ReadVector<SlotNumber, Hash32> getBlockRoots() {
    return blockRoots;
  }

  public ReadVector<SlotNumber, Hash32> getStateRoots() {
    return stateRoots;
  }
}
