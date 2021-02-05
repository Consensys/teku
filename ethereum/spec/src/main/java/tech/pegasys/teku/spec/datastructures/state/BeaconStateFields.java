/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state;

public enum BeaconStateFields {
  GENESIS_TIME,
  GENESIS_VALIDATORS_ROOT,
  SLOT,
  FORK,
  LATEST_BLOCK_HEADER,
  BLOCK_ROOTS,
  STATE_ROOTS,
  HISTORICAL_ROOTS,
  ETH1_DATA,
  ETH1_DATA_VOTES,
  ETH1_DEPOSIT_INDEX,
  VALIDATORS,
  BALANCES,
  RANDAO_MIXES,
  SLASHINGS,
  PREVIOUS_EPOCH_ATTESTATIONS,
  CURRENT_EPOCH_ATTESTATIONS,
  JUSTIFICATION_BITS,
  PREVIOUS_JUSTIFIED_CHECKPOINT,
  CURRENT_JUSTIFIED_CHECKPOINT,
  FINALIZED_CHECKPOINT,
  // HF1
  PREVIOUS_EPOCH_PARTICIPATION,
  CURRENT_EPOCH_PARTICIPATION
}
