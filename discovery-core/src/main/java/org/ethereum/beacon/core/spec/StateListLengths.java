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
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * State list lengths.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.1/specs/core/0_beacon-chain.md#state-list-lengths">State
 *     list lengths</a> in the spec.
 */
public interface StateListLengths {

  EpochNumber EPOCHS_PER_HISTORICAL_VECTOR = EpochNumber.of(1 << 16); // 65,536 epochs
  EpochNumber EPOCHS_PER_SLASHINGS_VECTOR = EpochNumber.of(1 << 13); // 8,192 epochs
  UInt64 HISTORICAL_ROOTS_LIMIT = UInt64.valueOf(1 << 24); // 16,777,216
  UInt64 VALIDATOR_REGISTRY_LIMIT = UInt64.valueOf(1L << 40); // 1,099,511,627,776 validators

  default EpochNumber getEpochsPerHistoricalVector() {
    return EPOCHS_PER_HISTORICAL_VECTOR;
  }

  default EpochNumber getEpochsPerSlashingsVector() {
    return EPOCHS_PER_SLASHINGS_VECTOR;
  }

  default UInt64 getHistoricalRootsLimit() {
    return HISTORICAL_ROOTS_LIMIT;
  }

  default UInt64 getValidatorRegistryLimit() {
    return VALIDATOR_REGISTRY_LIMIT;
  }
}
