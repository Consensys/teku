/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.logic.versions.deneb.helpers;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;

public class BeaconStateAccessorsDeneb extends BeaconStateAccessorsAltair {

  public BeaconStateAccessorsDeneb(
      final SpecConfigDeneb config,
      final Predicates predicates,
      final MiscHelpersDeneb miscHelpers) {
    super(config, predicates, miscHelpers);
  }

  /**
   * <a href="https://eips.ethereum.org/EIPS/eip-7044">EIP-7044: Perpetually Valid Signed Voluntary
   * Exits</a>
   */
  @Override
  public Bytes32 getVoluntaryExitDomain(
      final UInt64 epoch, final Fork fork, final Bytes32 genesisValidatorsRoot) {
    return miscHelpers.computeDomain(
        Domain.VOLUNTARY_EXIT,
        SpecConfigCapella.required(config).getCapellaForkVersion(),
        genesisValidatorsRoot);
  }

  /**
   * <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/beacon-chain.md#modified-get_attestation_participation_flag_indices">Modified
   * get_attestation_participation_flag_indices</a>
   */
  @Override
  protected boolean shouldSetTargetTimelinessFlag(
      final boolean isMatchingTarget, final UInt64 inclusionDelay) {
    return isMatchingTarget;
  }
}
