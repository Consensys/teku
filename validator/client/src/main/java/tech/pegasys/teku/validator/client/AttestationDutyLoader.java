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

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class AttestationDutyLoader
    extends AbstractDutyLoader<AttesterDuties, SlotBasedScheduledDuties<?, ?>> {

  private static final Logger LOG = LogManager.getLogger();

  // Attestation duty scheduling is batched in order to avoid expensive aggregation slot signing in
  // beginning of the epoch when a node is running a large number of validators
  private static final int BATCH_SIZE = 2500;
  private static final Duration BATCH_DELAY = Duration.ofSeconds(3);

  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final Function<
          Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
      scheduledDutiesFactory;
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions;
  private final Spec spec;
  private final boolean useDvtEndpoint;
  private final AsyncRunner asyncRunner;

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
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.beaconCommitteeSubscriptions = beaconCommitteeSubscriptions;
    this.spec = spec;
    this.useDvtEndpoint = useDvtEndpoint;
    this.asyncRunner = asyncRunner;
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

    final Optional<DvtAttestationAggregations> dvtAttestationAggregationsForEpoch =
        useDvtEndpoint
            ? Optional.of(
                new DvtAttestationAggregations(validatorApiChannel, duties.getDuties().size()))
            : Optional.empty();

    final List<List<AttesterDuty>> batchedDuties = Lists.partition(duties.getDuties(), BATCH_SIZE);

    final List<SafeFuture<?>> dutiesScheduling = new ArrayList<>();

    for (int i = 0; i < batchedDuties.size(); i++) {
      final List<AttesterDuty> batch = batchedDuties.get(i);
      if (i == 0) {
        // first batch has no delay
        dutiesScheduling.add(
            scheduleDuties(scheduledDuties, batch, dvtAttestationAggregationsForEpoch));
      } else {
        // consecutive batches are delayed by BATCH_DELAY * index of the batch
        dutiesScheduling.add(
            asyncRunner.runAfterDelay(
                () -> scheduleDuties(scheduledDuties, batch, dvtAttestationAggregationsForEpoch),
                BATCH_DELAY.multipliedBy(i)));
      }
    }

    return SafeFuture.allOf(dutiesScheduling.stream())
        .<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties)
        .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
  }

  private SafeFuture<Void> scheduleDuties(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final List<AttesterDuty> duties,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregationLoader) {
    return SafeFuture.allOf(
        duties.stream()
            .map(duty -> scheduleDuty(scheduledDuties, duty, dvtAttestationAggregationLoader))
            .toArray(SafeFuture[]::new));
  }

  private SafeFuture<Void> scheduleDuty(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final AttesterDuty duty,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregationLoader) {
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
        dvtAttestationAggregationLoader);
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
      final Optional<DvtAttestationAggregations> dvtAttestationAggregation) {
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> validator.getSigner().signAggregationSlot(slot, forkInfo))
        .thenCompose(
            slotSignature ->
                dvtAttestationAggregation
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
}
