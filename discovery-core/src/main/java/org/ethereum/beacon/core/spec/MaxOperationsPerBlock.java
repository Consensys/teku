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

/**
 * Constants limiting number of each beacon chain operation that a block can hold.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#max-operations-per-block">Max
 *     operations per block</a> in the spec.
 */
public interface MaxOperationsPerBlock {

  int MAX_PROPOSER_SLASHINGS = 1 << 4; // 16
  int MAX_ATTESTER_SLASHINGS = 1;
  int MAX_ATTESTATIONS = 1 << 7; // 128
  int MAX_DEPOSITS = 1 << 4; // 16
  int MAX_VOLUNTARY_EXITS = 1 << 4; // 16
  int MAX_TRANSFERS = 0;

  /* Values defined in the spec. */

  default int getMaxProposerSlashings() {
    return MAX_PROPOSER_SLASHINGS;
  }

  default int getMaxAttesterSlashings() {
    return MAX_ATTESTER_SLASHINGS;
  }

  default int getMaxAttestations() {
    return MAX_ATTESTATIONS;
  }

  default int getMaxDeposits() {
    return MAX_DEPOSITS;
  }

  default int getMaxVoluntaryExits() {
    return MAX_VOLUNTARY_EXITS;
  }

  default int getMaxTransfers() {
    return MAX_TRANSFERS;
  }
}
