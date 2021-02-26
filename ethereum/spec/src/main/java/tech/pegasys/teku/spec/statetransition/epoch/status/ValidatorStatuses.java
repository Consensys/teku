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

package tech.pegasys.teku.spec.statetransition.epoch.status;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.util.AttestationUtil;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.ValidatorsUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class ValidatorStatuses {
  private final List<ValidatorStatus> statuses;
  private final TotalBalances totalBalances;

  private ValidatorStatuses(
      final List<ValidatorStatus> statuses, final TotalBalances totalBalances) {
    this.statuses = statuses;
    this.totalBalances = totalBalances;
  }

  public static ValidatorStatuses create(
      final BeaconState state,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorsUtil validatorsUtil,
      final AttestationUtil attestationUtil) {
    final SSZList<Validator> validators = state.getValidators();

    final UInt64 currentEpoch = beaconStateUtil.getCurrentEpoch(state);
    final UInt64 previousEpoch = beaconStateUtil.getPreviousEpoch(state);

    final List<ValidatorStatus> statuses =
        validators.stream()
            .map(
                validator ->
                    ValidatorStatus.create(validator, previousEpoch, currentEpoch, validatorsUtil))
            .collect(Collectors.toCollection(() -> new ArrayList<>(validators.size())));

    processAttestations(
        statuses, state, previousEpoch, currentEpoch, beaconStateUtil, attestationUtil);

    final TotalBalances totalBalances = createTotalBalances(statuses);
    BeaconStateCache.getTransitionCaches(state).setLatestTotalBalances(totalBalances);
    BeaconStateCache.getTransitionCaches(state)
        .getTotalActiveBalance()
        .get(currentEpoch, __ -> totalBalances.getCurrentEpoch());

    return new ValidatorStatuses(statuses, totalBalances);
  }

  private static void processAttestations(
      final List<ValidatorStatus> statuses,
      final BeaconState state,
      final UInt64 previousEpoch,
      final UInt64 currentEpoch,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil) {
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
                    matchesEpochStartBlock(state, currentEpoch, target.getRoot(), beaconStateUtil);
              } else if (target.getEpoch().equals(previousEpoch)) {
                updates.previousEpochAttester = true;

                updates.inclusionInfo =
                    Optional.of(
                        new InclusionInfo(
                            attestation.getInclusion_delay(), attestation.getProposer_index()));

                if (matchesEpochStartBlock(
                    state, previousEpoch, target.getRoot(), beaconStateUtil)) {
                  updates.previousEpochTargetAttester = true;

                  updates.previousEpochHeadAttester =
                      beaconStateUtil
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

  private static boolean matchesEpochStartBlock(
      final BeaconState state,
      final UInt64 currentEpoch,
      final Bytes32 root,
      final BeaconStateUtil beaconStateUtil) {
    return beaconStateUtil.getBlockRoot(state, currentEpoch).equals(root);
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

  @VisibleForTesting
  static TotalBalances createTotalBalances(final List<ValidatorStatus> statuses) {
    UInt64 currentEpoch = UInt64.ZERO;
    UInt64 previousEpoch = UInt64.ZERO;
    UInt64 currentEpochAttesters = UInt64.ZERO;
    UInt64 currentEpochTargetAttesters = UInt64.ZERO;
    UInt64 previousEpochAttesters = UInt64.ZERO;
    UInt64 previousEpochTargetAttesters = UInt64.ZERO;
    UInt64 previousEpochHeadAttesters = UInt64.ZERO;

    for (ValidatorStatus status : statuses) {
      final UInt64 balance = status.getCurrentEpochEffectiveBalance();
      if (status.isActiveInCurrentEpoch()) {
        currentEpoch = currentEpoch.plus(balance);
      }
      if (status.isActiveInPreviousEpoch()) {
        previousEpoch = previousEpoch.plus(balance);
      }

      if (status.isSlashed()) {
        continue;
      }
      if (status.isCurrentEpochAttester()) {
        currentEpochAttesters = currentEpochAttesters.plus(balance);

        if (status.isCurrentEpochTargetAttester()) {
          currentEpochTargetAttesters = currentEpochTargetAttesters.plus(balance);
        }
      }

      if (status.isPreviousEpochAttester()) {
        previousEpochAttesters = previousEpochAttesters.plus(balance);
        if (status.isPreviousEpochTargetAttester()) {
          previousEpochTargetAttesters = previousEpochTargetAttesters.plus(balance);
        }
        if (status.isPreviousEpochHeadAttester()) {
          previousEpochHeadAttesters = previousEpochHeadAttesters.plus(balance);
        }
      }
    }
    return new TotalBalances(
        currentEpoch,
        previousEpoch,
        currentEpochAttesters,
        currentEpochTargetAttesters,
        previousEpochAttesters,
        previousEpochTargetAttesters,
        previousEpochHeadAttesters);
  }
}
