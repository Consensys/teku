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
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;
import static tech.pegasys.teku.validator.client.AttestationDutyScheduler.LOOKAHEAD_EPOCHS;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.NodeDataUnavailableException;
import tech.pegasys.teku.validator.client.ValidatorIndexProvider;

public class AttestationDutyLogger extends DutyLogger {

  private static final String DUTY_NAME = "attestation / aggregation";
  private static final int CACHE_SIZE = LOOKAHEAD_EPOCHS + 1;
  private final Map<UInt64, AttesterDuties> cachedDuties = createCacheMap(CACHE_SIZE);
  private final ValidatorIndexProvider validatorIndexProvider;
  private final ValidatorApiChannel validatorApiChannel;

  public AttestationDutyLogger(
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec) {
    super(spec);
    this.validatorIndexProvider = validatorIndexProvider;
    this.validatorApiChannel = validatorApiChannel;
  }

  private SafeFuture<List<AttesterDuty>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Collections.emptyList());
    }
    if (isCacheUptodate(epoch)) {
      return SafeFuture.of(CompletableFuture.completedFuture(combineDuties()));
    }
    List<SafeFuture<AttesterDuties>> queries = new ArrayList<>();
    for (UInt64 i = epoch; i.isLessThanOrEqualTo(epoch.plus(LOOKAHEAD_EPOCHS)); i = i.plus(1)) {
      if (!cachedDuties.containsKey(i)) {
        final UInt64 currentI = i;
        queries.add(
            validatorApiChannel
                .getAttestationDuties(i, validatorIndices)
                .thenApply(
                    maybeDuties ->
                        maybeDuties.orElseThrow(
                            () ->
                                new NodeDataUnavailableException(
                                    "Duties could not be calculated because chain data was not yet available")))
                .thenPeek(attesterDuties -> cachedDuties.put(currentI, attesterDuties)));
      }
    }
    return SafeFuture.collectAll(queries.stream()).thenApply(__ -> combineDuties());
  }

  private List<AttesterDuty> combineDuties() {
    List<AttesterDuty> allDuties = new ArrayList<>();
    cachedDuties.values().forEach(duties -> allDuties.addAll(duties.getDuties()));
    return allDuties;
  }

  @Override
  public void onEpochStartSlot(final UInt64 slot) {
    onSlot(slot);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    SafeFuture<IntCollection> validatorIndices = validatorIndexProvider.getValidatorIndices();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    validatorIndices
        .thenPeek(
            indices ->
                requestDuties(currentEpoch, indices)
                    .thenPeek(duties -> logSlotDuties(duties, slot))
                    .reportExceptions())
        .reportExceptions();
  }

  protected void logSlotDuties(final List<AttesterDuty> duties, UInt64 slot) {
    int lastAttestationDutyInSlotDelay = (spec.getSecondsPerSlot(slot) * 2) / INTERVALS_PER_SLOT;
    Map<BLSPublicKey, List<AttesterDuty>> actualDuties =
        duties.stream()
            .filter(duty -> duty.getSlot().isGreaterThanOrEqualTo(slot))
            .collect(
                Collectors.groupingBy(
                    AttesterDuty::getPublicKey,
                    Collectors.mapping(Function.identity(), Collectors.toList())));
    actualDuties.forEach(
        (publicKey, value) -> {
          List<String> expectedDutyTimes =
              value.stream()
                  .map(
                      duty ->
                          calculateSlotDeltaSeconds(slot, duty.getSlot())
                              .plus(lastAttestationDutyInSlotDelay))
                  .sorted()
                  .map(UInt64::toString)
                  .collect(Collectors.toList());
          VALIDATOR_LOGGER.scheduledDuties(
              DUTY_NAME, expectedDutyTimes, publicKey.toAbbreviatedString());
        });
  }

  protected boolean isCacheUptodate(UInt64 epoch) {
    boolean result = cachedDuties.containsKey(epoch);
    for (UInt64 i = epoch.plus(1);
        i.isLessThanOrEqualTo(epoch.plus(LOOKAHEAD_EPOCHS));
        i = i.plus(1)) {
      result &= cachedDuties.containsKey(i);
    }
    return result;
  }

  @Override
  protected void invalidate() {
    cachedDuties.clear();
  }
}
