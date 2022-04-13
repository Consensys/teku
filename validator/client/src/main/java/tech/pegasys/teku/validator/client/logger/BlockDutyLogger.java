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

package tech.pegasys.teku.validator.client.logger;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.NodeDataUnavailableException;
import tech.pegasys.teku.validator.client.ValidatorIndexProvider;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class BlockDutyLogger extends DutyLogger {
  public static final String DUTY_NAME = "block proposal";
  private final OwnedValidators validators;
  private Pair<UInt64, Optional<List<ProposerDuty>>> cachedDuties =
      Pair.of(UInt64.MAX_VALUE, Optional.empty());
  private final ValidatorIndexProvider validatorIndexProvider;
  private final ValidatorApiChannel validatorApiChannel;

  public BlockDutyLogger(
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorIndexProvider validatorIndexProvider,
      final OwnedValidators validators,
      final Spec spec) {
    super(spec);
    this.validatorIndexProvider = validatorIndexProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.validators = validators;
  }

  protected SafeFuture<Optional<List<ProposerDuty>>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    if (isCacheUptodate(epoch)) {
      return SafeFuture.of(CompletableFuture.completedFuture(cachedDuties.getRight()));
    }
    return validatorApiChannel
        .getProposerDuties(epoch)
        .thenApply(
            (Function<Optional<ProposerDuties>, Optional<List<ProposerDuty>>>)
                pd -> {
                  if (pd.isEmpty()) {
                    return Optional.empty();
                  } else {
                    ProposerDuties proposerDuties = pd.get();
                    return Optional.of(
                        proposerDuties.getDuties().stream()
                            .filter(duty -> validatorIndices.contains(duty.getValidatorIndex()))
                            .collect(Collectors.toList()));
                  }
                })
        .thenPeek(blockDuties -> cachedDuties = Pair.of(epoch, blockDuties));
  }

  @Override
  public void onSlot(final UInt64 slot) {
    SafeFuture<IntCollection> validatorIndices = validatorIndexProvider.getValidatorIndices();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    validatorIndices
        .thenPeek(
            indices ->
                requestDuties(currentEpoch, indices)
                    .thenApply(
                        maybeDuties ->
                            maybeDuties.orElseThrow(
                                () ->
                                    new NodeDataUnavailableException(
                                        "Duties could not be calculated because chain data was not yet available")))
                    .thenPeek(duties -> logSlotDuties(duties, slot))
                    .reportExceptions())
        .reportExceptions();
  }

  private void logSlotDuties(final List<ProposerDuty> duties, UInt64 slot) {
    List<ProposerDuty> actualDuties =
        duties.stream()
            .filter(duty -> duty.getSlot().isGreaterThanOrEqualTo(slot))
            .collect(Collectors.toList());
    if (actualDuties.isEmpty()) {
      return;
    }
    Map<BLSPublicKey, List<ProposerDuty>> proposerDutyMap =
        duties.stream()
            .filter(duty -> duty.getSlot().isGreaterThanOrEqualTo(slot))
            .collect(
                Collectors.groupingBy(
                    ProposerDuty::getPublicKey,
                    Collectors.mapping(Function.identity(), Collectors.toList())));
    proposerDutyMap.forEach(
        (publicKey, value) -> {
          List<String> expectedDutyTimes =
              value.stream()
                  .map(duty -> calculateSlotDeltaSeconds(slot, duty.getSlot()))
                  .sorted()
                  .map(UInt64::toString)
                  .collect(Collectors.toList());
          VALIDATOR_LOGGER.scheduledDuties(
              DUTY_NAME, expectedDutyTimes, publicKey.toAbbreviatedString());
        });
  }

  @Override
  public void onEpochStartSlot(final UInt64 slot) {
    SafeFuture<IntCollection> validatorIndices = validatorIndexProvider.getValidatorIndices();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    validatorIndices
        .thenPeek(
            indices ->
                requestDuties(currentEpoch, indices)
                    .thenApply(
                        maybeDuties ->
                            maybeDuties.orElseThrow(
                                () ->
                                    new NodeDataUnavailableException(
                                        "Duties could not be calculated because chain data was not yet available")))
                    .thenPeek(duties -> logEpochDuties(duties, slot, currentEpoch))
                    .reportExceptions())
        .reportExceptions();
  }

  private void logEpochDuties(final List<ProposerDuty> duties, UInt64 slot, UInt64 epoch) {
    Map<BLSPublicKey, List<ProposerDuty>> proposerDutyMap =
        duties.stream()
            .filter(duty -> duty.getSlot().isGreaterThanOrEqualTo(slot))
            .collect(
                Collectors.groupingBy(
                    ProposerDuty::getPublicKey,
                    Collectors.mapping(Function.identity(), Collectors.toList())));
    final UInt64 nextEpochFirstSlot = spec.computeStartSlotAtEpoch(epoch.plus(1));
    UInt64 expectedNoDutyTime = calculateSlotDeltaSeconds(slot, nextEpochFirstSlot);
    validators
        .getActiveValidators()
        .forEach(
            validator -> {
              if (proposerDutyMap.containsKey(validator.getPublicKey())) {
                List<ProposerDuty> validatorDuties = proposerDutyMap.get(validator.getPublicKey());
                BLSPublicKey publicKey = validatorDuties.get(0).getPublicKey();
                List<String> expectedDutyTimes =
                    validatorDuties.stream()
                        .map(duty -> calculateSlotDeltaSeconds(slot, duty.getSlot()))
                        .sorted()
                        .map(UInt64::toString)
                        .collect(Collectors.toList());
                VALIDATOR_LOGGER.scheduledDutiesForEpoch(
                    DUTY_NAME,
                    epoch.toString(),
                    expectedNoDutyTime.toString(),
                    expectedDutyTimes,
                    publicKey.toAbbreviatedString());
              } else {
                VALIDATOR_LOGGER.scheduledDutiesForEpoch(
                    DUTY_NAME,
                    epoch.toString(),
                    expectedNoDutyTime.toString(),
                    Collections.emptyList(),
                    validator.getPublicKey().toAbbreviatedString());
              }
            });
  }

  protected boolean isCacheUptodate(UInt64 epoch) {
    return cachedDuties.getLeft().equals(epoch);
  }

  @Override
  protected void invalidate() {
    this.cachedDuties = Pair.of(UInt64.MAX_VALUE, Optional.empty());
  }
}
