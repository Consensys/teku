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

package tech.pegasys.teku.spec.logic.versions.phase0.statetransition.epoch;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.AbstractValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.InclusionInfo;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

public class ValidatorStatusFactoryPhase0 extends AbstractValidatorStatusFactory {

  public ValidatorStatusFactoryPhase0(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final BeaconStateAccessors beaconStateAccessors,
      final Predicates predicates) {
    super(specConfig, beaconStateUtil, attestationUtil, predicates, beaconStateAccessors);
  }

  @Override
  protected void processParticipation(
      final List<ValidatorStatus> statuses,
      final BeaconState genericState,
      final UInt64 previousEpoch,
      final UInt64 currentEpoch) {

    final BeaconStatePhase0 state = BeaconStatePhase0.required(genericState);

    Stream.concat(
            state.getPrevious_epoch_attestations().stream(),
            state.getCurrent_epoch_attestations().stream())
        .forEach(
            attestation -> {
              final AttestationData data = attestation.getData();

              final AttestationUpdates updates = new AttestationUpdates();
              final Checkpoint target = data.getTarget();
              if (target.getEpoch().equals(currentEpoch)) {
                updates.currentEpochSourceAttester = true;
                updates.currentEpochTargetAttester =
                    matchesEpochStartBlock(state, currentEpoch, target.getRoot());
              } else if (target.getEpoch().equals(previousEpoch)) {
                updates.previousEpochSourceAttester = true;

                updates.inclusionInfo =
                    Optional.of(
                        new InclusionInfo(
                            attestation.getInclusion_delay(), attestation.getProposer_index()));

                if (matchesEpochStartBlock(state, previousEpoch, target.getRoot())) {
                  updates.previousEpochTargetAttester = true;

                  updates.previousEpochHeadAttester =
                      beaconStateAccessors
                          .getBlockRootAtSlot(state, data.getSlot())
                          .equals(data.getBeacon_block_root());
                }
              }

              // Apply flags to attestingIndices
              attestationUtil
                  .streamAttestingIndices(state, data, attestation.getAggregation_bits())
                  .mapToObj(statuses::get)
                  .forEach(updates::apply);
            });
  }

  private static class AttestationUpdates {
    private boolean currentEpochSourceAttester = false;
    private boolean currentEpochTargetAttester = false;
    private boolean previousEpochSourceAttester = false;
    private boolean previousEpochTargetAttester = false;
    private boolean previousEpochHeadAttester = false;
    private Optional<InclusionInfo> inclusionInfo = Optional.empty();

    public void apply(final ValidatorStatus status) {
      status.updateCurrentEpochSourceAttester(currentEpochSourceAttester);
      status.updateCurrentEpochTargetAttester(currentEpochTargetAttester);
      status.updatePreviousEpochSourceAttester(previousEpochSourceAttester);
      status.updatePreviousEpochTargetAttester(previousEpochTargetAttester);
      status.updatePreviousEpochHeadAttester(previousEpochHeadAttester);
      status.updateInclusionInfo(inclusionInfo);
    }
  }
}
