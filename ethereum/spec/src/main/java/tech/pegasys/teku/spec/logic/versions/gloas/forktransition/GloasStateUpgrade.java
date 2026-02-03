/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.forktransition;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GloasStateUpgrade implements StateUpgrade<BeaconStateFulu> {

  private final SpecConfigGloas specConfig;
  private final SchemaDefinitionsGloas schemaDefinitions;
  private final BeaconStateAccessorsGloas beaconStateAccessors;
  private final PredicatesGloas predicates;
  private final BeaconStateMutatorsGloas beaconStateMutators;

  public GloasStateUpgrade(
      final SpecConfigGloas specConfig,
      final SchemaDefinitionsGloas schemaDefinitions,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final PredicatesGloas predicates,
      final BeaconStateMutatorsGloas beaconStateMutators) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.predicates = predicates;
    this.beaconStateMutators = beaconStateMutators;
  }

  @Override
  public BeaconStateGloas upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateFulu preStateFulu = BeaconStateFulu.required(preState);

    return BeaconStateGloas.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedGloas(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getGloasForkVersion(),
                      epoch));

              state.setPreviousEpochParticipation(preStateFulu.getPreviousEpochParticipation());
              state.setCurrentEpochParticipation(preStateFulu.getCurrentEpochParticipation());
              state.setInactivityScores(preStateFulu.getInactivityScores());
              state.setCurrentSyncCommittee(preStateFulu.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateFulu.getNextSyncCommittee());

              // New in Gloas
              final Bytes32 latestBlockHash =
                  preStateFulu.getLatestExecutionPayloadHeaderRequired().getBlockHash();
              state.setLatestExecutionPayloadBid(
                  schemaDefinitions
                      .getExecutionPayloadBidSchema()
                      .create(
                          Bytes32.ZERO,
                          Bytes32.ZERO,
                          latestBlockHash,
                          Bytes32.ZERO,
                          Bytes20.ZERO,
                          UInt64.ZERO,
                          UInt64.ZERO,
                          UInt64.ZERO,
                          UInt64.ZERO,
                          UInt64.ZERO,
                          schemaDefinitions.getBlobKzgCommitmentsSchema().of()));

              state.setNextWithdrawalIndex(preStateFulu.getNextWithdrawalIndex());
              state.setNextWithdrawalValidatorIndex(preStateFulu.getNextWithdrawalValidatorIndex());
              state.setHistoricalSummaries(preStateFulu.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(preStateFulu.getDepositRequestsStartIndex());
              state.setDepositBalanceToConsume(preStateFulu.getDepositBalanceToConsume());
              state.setExitBalanceToConsume(preStateFulu.getExitBalanceToConsume());
              state.setEarliestExitEpoch(preStateFulu.getEarliestExitEpoch());
              state.setConsolidationBalanceToConsume(
                  preStateFulu.getConsolidationBalanceToConsume());
              state.setEarliestConsolidationEpoch(preStateFulu.getEarliestConsolidationEpoch());
              state.setPendingDeposits(preStateFulu.getPendingDeposits());
              state.setPendingPartialWithdrawals(preStateFulu.getPendingPartialWithdrawals());
              state.setPendingConsolidations(preStateFulu.getPendingConsolidations());
              state.setProposerLookahead(preStateFulu.getProposerLookahead());

              // New in Gloas
              state.setBuilders(
                  BeaconStateSchemaGloas.required(state.getBeaconStateSchema())
                      .getBuildersSchema()
                      .of());
              state.setNextWithdrawalBuilderIndex(UInt64.ZERO);
              final SszBitvector executionPayloadAvailability =
                  schemaDefinitions
                      .getExecutionPayloadAvailabilitySchema()
                      .ofBits(IntStream.range(0, specConfig.getSlotsPerHistoricalRoot()).toArray());
              state.setExecutionPayloadAvailability(executionPayloadAvailability);
              final List<BuilderPendingPayment> builderPendingPayments =
                  Collections.nCopies(
                      2 * specConfig.getSlotsPerEpoch(),
                      schemaDefinitions.getBuilderPendingPaymentSchema().getDefault());
              state.setBuilderPendingPayments(
                  schemaDefinitions
                      .getBuilderPendingPaymentsSchema()
                      .createFromElements(builderPendingPayments));
              state.setBuilderPendingWithdrawals(
                  schemaDefinitions.getBuilderPendingWithdrawalsSchema().of());
              state.setLatestBlockHash(latestBlockHash);
              state.setPayloadExpectedWithdrawals(
                  schemaDefinitions
                      .getExecutionPayloadSchema()
                      .getWithdrawalsSchemaRequired()
                      .of());

              onboardBuildersFromPendingDeposits(state);
            });
  }

  /** Applies any pending deposit for builders, effectively onboarding builders at the fork. */
  private void onboardBuildersFromPendingDeposits(final MutableBeaconStateGloas state) {
    final Set<BLSPublicKey> validatorPubkeys =
        state.getValidators().stream().map(Validator::getPublicKey).collect(Collectors.toSet());
    final SszList<PendingDeposit> pendingDeposits =
        state.getPendingDeposits().stream()
            .filter(
                deposit -> {
                  if (validatorPubkeys.contains(deposit.getPublicKey())) {
                    return true;
                  }
                  if (predicates.isBuilderWithdrawalCredential(
                      deposit.getWithdrawalCredentials())) {
                    beaconStateMutators.applyDepositForBuilder(
                        state,
                        deposit.getPublicKey(),
                        deposit.getWithdrawalCredentials(),
                        deposit.getAmount(),
                        deposit.getSignature(),
                        deposit.getSlot());
                    return false;
                  }
                  return true;
                })
            .collect(schemaDefinitions.getPendingDepositsSchema().collector());
    state.setPendingDeposits(pendingDeposits);
  }
}
