/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.client;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class AttestationDutyLoader
    extends AbstractDutyLoader<AttesterDuties, SlotBasedScheduledDuties<?, ?>>
    implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  // Attestation duty scheduling is batched by slots and delays are added in order to avoid
  // expensive aggregation slot signing in beginning of the epoch when a node is running a large
  // number of validators
  @VisibleForTesting
  record SlotBatchingOptions(
      int minSizeToBatchBySlot,
      int currentEpochSlotsToScheduleBeforeDelay,
      Duration currentEpochSchedulingDelay,
      int futureEpochSlotsToScheduleBeforeDelay,
      Duration futureEpochSchedulingDelay) {}

  private static final SlotBatchingOptions DEFAULT_BATCHING_OPTIONS =
      new SlotBatchingOptions(1000, 4, Duration.ofMillis(500), 1, Duration.ofSeconds(1));

  private final AtomicReference<UInt64> currentSlot = new AtomicReference<>(UInt64.ZERO);

  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final Function<
          Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
      scheduledDutiesFactory;
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions;
  private final Spec spec;
  private final boolean useDvtEndpoint;
  private final AsyncRunner asyncRunner;
  private final SlotBatchingOptions slotBatchingOptions;

  public AttestationDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions,
      final Spec spec,
      final boolean useDvtEndpoint,
      final AsyncRunner asyncRunner) {
    this(
        validatorApiChannel,
        forkProvider,
        scheduledDutiesFactory,
        validators,
        validatorIndexProvider,
        beaconCommitteeSubscriptions,
        spec,
        useDvtEndpoint,
        asyncRunner,
        DEFAULT_BATCHING_OPTIONS);
  }

  @VisibleForTesting
  AttestationDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions,
      final Spec spec,
      final boolean useDvtEndpoint,
      final AsyncRunner asyncRunner,
      final SlotBatchingOptions slotBatchingOptions) {
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.beaconCommitteeSubscriptions = beaconCommitteeSubscriptions;
    this.spec = spec;
    this.useDvtEndpoint = useDvtEndpoint;
    this.asyncRunner = asyncRunner;
    this.slotBatchingOptions = slotBatchingOptions;
  }

  @Override
  protected SafeFuture<Optional<AttesterDuties>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return validatorApiChannel.getAttestationDuties(epoch, validatorIndices);
  }

  @Override
  protected SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final AttesterDuties duties) {
    final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties =
        scheduledDutiesFactory.apply(duties.getDependentRoot());

    final Optional<DvtAttestationAggregations> dvtAttestationAggregations =
        useDvtEndpoint
            ? Optional.of(
                new DvtAttestationAggregations(validatorApiChannel, duties.getDuties().size()))
            : Optional.empty();

    if (duties.getDuties().size() < slotBatchingOptions.minSizeToBatchBySlot) {
      return scheduleDuties(scheduledDuties, duties.getDuties(), dvtAttestationAggregations)
          .<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties)
          .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
    }

    // every X amount of slots a delay is added to the scheduling (values are based on if current or
    // future epoch)
    final boolean isCurrentEpoch =
        epoch.isLessThanOrEqualTo(spec.computeEpochAtSlot(currentSlot.get()));
    final int slotsBeforeDelay =
        isCurrentEpoch
            ? slotBatchingOptions.currentEpochSlotsToScheduleBeforeDelay
            : slotBatchingOptions.futureEpochSlotsToScheduleBeforeDelay;
    final Duration schedulingDelay =
        isCurrentEpoch
            ? slotBatchingOptions.currentEpochSchedulingDelay
            : slotBatchingOptions.futureEpochSchedulingDelay;

    final Map<UInt64, List<AttesterDuty>> dutiesBySlot =
        duties.getDuties().stream()
            .collect(
                Collectors.groupingBy(AttesterDuty::getSlot, TreeMap::new, Collectors.toList()));

    SafeFuture<Void> dutiesScheduling = SafeFuture.COMPLETE;

    int i = 0;
    for (final List<AttesterDuty> dutiesForSlot : dutiesBySlot.values()) {
      // no delay at start
      if (i != 0 && i % slotsBeforeDelay == 0) {
        dutiesScheduling =
            dutiesScheduling.thenCompose(
                __ ->
                    asyncRunner.runAfterDelay(
                        () ->
                            scheduleDuties(
                                scheduledDuties, dutiesForSlot, dvtAttestationAggregations),
                        schedulingDelay));
      } else {
        dutiesScheduling =
            dutiesScheduling.thenCompose(
                __ -> scheduleDuties(scheduledDuties, dutiesForSlot, dvtAttestationAggregations));
      }
      i++;
    }

    return dutiesScheduling
        .<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties)
        .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
  }

  private SafeFuture<Void> scheduleDuties(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final List<AttesterDuty> duties,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregations) {
    return SafeFuture.allOf(
        duties.stream()
            .map(duty -> scheduleDuty(scheduledDuties, duty, dvtAttestationAggregations))
            .toArray(SafeFuture[]::new));
  }

  private SafeFuture<Void> scheduleDuty(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final AttesterDuty duty,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregations) {
    final Optional<Validator> maybeValidator = validators.getValidator(duty.getPublicKey());
    if (maybeValidator.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final Validator validator = maybeValidator.get();
    final int aggregatorModulo =
        spec.atSlot(duty.getSlot())
            .getValidatorsUtil()
            .getAggregatorModulo(duty.getCommitteeLength());

    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        scheduleAttestationProduction(
            scheduledDuties,
            duty.getCommitteeIndex(),
            duty.getValidatorCommitteeIndex(),
            duty.getCommitteeLength(),
            duty.getValidatorIndex(),
            validator,
            duty.getSlot());

    return scheduleAggregation(
        scheduledDuties,
        duty.getCommitteeIndex(),
        duty.getCommitteesAtSlot(),
        duty.getValidatorIndex(),
        validator,
        duty.getSlot(),
        aggregatorModulo,
        unsignedAttestationFuture,
        dvtAttestationAggregations);
  }

  private SafeFuture<Optional<AttestationData>> scheduleAttestationProduction(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final int attestationCommitteeSize,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot) {
    return scheduledDuties.scheduleProduction(
        slot,
        validator,
        duty ->
            duty.addValidator(
                validator,
                attestationCommitteeIndex,
                attestationCommitteePosition,
                validatorIndex,
                attestationCommitteeSize));
  }

  private SafeFuture<Void> scheduleAggregation(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final int attestationCommitteeIndex,
      final int committeesAtSlot,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot,
      final int aggregatorModulo,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregations) {
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> validator.getSigner().signAggregationSlot(slot, forkInfo))
        .thenCompose(
            slotSignature ->
                dvtAttestationAggregations
                    .map(
                        dvt ->
                            dvt.getCombinedSelectionProofFuture(
                                validatorIndex, slot, slotSignature))
                    .orElse(SafeFuture.completedFuture(slotSignature)))
        .thenAccept(
            slotSignature -> {
              final SpecVersion specVersion = spec.atSlot(slot);
              final boolean isAggregator =
                  specVersion.getValidatorsUtil().isAggregator(slotSignature, aggregatorModulo);
              beaconCommitteeSubscriptions.subscribeToBeaconCommittee(
                  new CommitteeSubscriptionRequest(
                      validatorIndex,
                      attestationCommitteeIndex,
                      UInt64.valueOf(committeesAtSlot),
                      slot,
                      isAggregator));
              if (isAggregator) {
                scheduledDuties.scheduleAggregation(
                    slot,
                    validator,
                    duty ->
                        duty.addValidator(
                            validator,
                            validatorIndex,
                            slotSignature,
                            attestationCommitteeIndex,
                            unsignedAttestationFuture));
              }
            })
        .exceptionally(
            error -> {
              LOG.error("Failed to schedule aggregation duties", error);
              return null;
            });
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot.set(slot);
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {}

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {}

  @Override
  public void onBlockProductionDue(final UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {}

  @Override
  public void onAttesterSlashing(final AttesterSlashing attesterSlashing) {}

  @Override
  public void onProposerSlashing(final ProposerSlashing proposerSlashing) {}

  @Override
  public void onUpdatedValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean possibleMissingEvents) {}
}
