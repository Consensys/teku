/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core.epoch.status;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.AttestationUtil;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class ValidatorStatuses {
  private final List<ValidatorStatus> statuses;
  private final TotalBalances totalBalances;

  private ValidatorStatuses(
      final List<ValidatorStatus> statuses, final TotalBalances totalBalances) {
    this.statuses = statuses;
    this.totalBalances = totalBalances;
  }

  public static ValidatorStatuses create(final BeaconState state) {
    final SSZList<Validator> validators = state.getValidators();

    final UInt64 currentEpoch = BeaconStateUtil.get_current_epoch(state);
    final UInt64 previousEpoch = BeaconStateUtil.get_previous_epoch(state);

    final List<ValidatorStatus> statuses =
        validators.stream()
            .map(validator -> ValidatorStatus.create(validator, previousEpoch, currentEpoch))
            .collect(Collectors.toCollection(() -> new ArrayList<>(validators.size())));

    processAttestations(statuses, state, previousEpoch, currentEpoch);

    final TotalBalances totalBalances = TotalBalances.create(statuses);
    TotalBalances.latestTotalBalances = Optional.of(totalBalances);

    return new ValidatorStatuses(statuses, totalBalances);
  }

  private static void processAttestations(
      final List<ValidatorStatus> statuses,
      final BeaconState state,
      final UInt64 previousEpoch,
      final UInt64 currentEpoch) {
    Stream.concat(
            state.getPrevious_epoch_attestations().stream(),
            state.getCurrent_epoch_attestations().stream())
        .forEach(
            attestation -> {
              final AttestationData data = attestation.getData();

              final AttestationUpdates updates = new AttestationUpdates();
              final Checkpoint target = data.getTarget();
              if (target.getEpoch().equals(currentEpoch)) {
                updates.currentEpochAttester = true;
                updates.currentEpochTargetAttester =
                    matchesEpochStartBlock(state, currentEpoch, target.getRoot());
              } else if (target.getEpoch().equals(previousEpoch)) {
                updates.previousEpochAttester = true;

                updates.inclusionInfo =
                    Optional.of(
                        new InclusionInfo(
                            attestation.getInclusion_delay(), attestation.getProposer_index()));

                if (matchesEpochStartBlock(state, previousEpoch, target.getRoot())) {
                  updates.previousEpochTargetAttester = true;

                  updates.previousEpochHeadAttester =
                      get_block_root_at_slot(state, data.getSlot())
                          .equals(data.getBeacon_block_root());
                }
              }

              // Apply flags to attestingIndices
              AttestationUtil.stream_attesting_indices(
                      state, data, attestation.getAggregation_bits())
                  .mapToObj(statuses::get)
                  .forEach(updates::apply);
            });
  }

  private static boolean matchesEpochStartBlock(
      final BeaconState state, final UInt64 currentEpoch, final Bytes32 root) {
    return BeaconStateUtil.get_block_root(state, currentEpoch).equals(root);
  }

  public TotalBalances getTotalBalances() {
    return totalBalances;
  }

  public List<ValidatorStatus> getStatuses() {
    return statuses;
  }

  public int getValidatorCount() {
    return statuses.size();
  }

  private static class AttestationUpdates {
    private boolean currentEpochAttester = false;
    private boolean currentEpochTargetAttester = false;
    private boolean previousEpochAttester = false;
    private boolean previousEpochTargetAttester = false;
    private boolean previousEpochHeadAttester = false;
    private Optional<InclusionInfo> inclusionInfo = Optional.empty();

    public void apply(final ValidatorStatus status) {
      status.updateCurrentEpochAttester(currentEpochAttester);
      status.updateCurrentEpochTargetAttester(currentEpochTargetAttester);
      status.updatePreviousEpochAttester(previousEpochAttester);
      status.updatePreviousEpochTargetAttester(previousEpochTargetAttester);
      status.updatePreviousEpochHeadAttester(previousEpochHeadAttester);
      status.updateInclusionInfo(inclusionInfo);
    }
  }
}
