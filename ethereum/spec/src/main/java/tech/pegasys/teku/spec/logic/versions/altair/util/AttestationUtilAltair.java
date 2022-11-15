/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.altair.util;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.attestation.AttestationWorthinessChecker;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.attestation.AttestationWorthinessCheckerAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilAltair extends AttestationUtil {
  public AttestationUtilAltair(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  @Override
  public AttestationWorthinessChecker createAttestationWorthinessChecker(final BeaconState state) {
    final UInt64 currentSlot = state.getSlot();
    final UInt64 startSlot =
        miscHelpers.computeStartSlotAtEpoch(miscHelpers.computeEpochAtSlot(currentSlot));

    final Bytes32 expectedAttestationTarget =
        startSlot.compareTo(currentSlot) == 0 || currentSlot.compareTo(startSlot) <= 0
            ? state.getLatestBlockHeader().getRoot()
            : beaconStateAccessors.getBlockRootAtSlot(state, startSlot);

    final UInt64 oldestWorthySlotForSourceReward =
        state.getSlot().minusMinZero(specConfig.getSquareRootSlotsPerEpoch());
    return new AttestationWorthinessCheckerAltair(
        expectedAttestationTarget, oldestWorthySlotForSourceReward);
  }
}
