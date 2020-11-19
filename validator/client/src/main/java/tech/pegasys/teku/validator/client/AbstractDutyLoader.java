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

package tech.pegasys.teku.validator.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public abstract class AbstractDutyLoader<D> implements DutyLoader {

  private static final Logger LOG = LogManager.getLogger();
  private final Supplier<ScheduledDuties> scheduledDutiesFactory;
  protected final Map<BLSPublicKey, Validator> validators;
  private final ValidatorIndexProvider validatorIndexProvider;

  protected AbstractDutyLoader(
      final Supplier<ScheduledDuties> scheduledDutiesFactory,
      final Map<BLSPublicKey, Validator> validators,
      final ValidatorIndexProvider validatorIndexProvider) {
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.validators = validators;
    this.validatorIndexProvider = validatorIndexProvider;
  }

  @Override
  public SafeFuture<ScheduledDuties> loadDutiesForEpoch(final UInt64 epoch) {
    LOG.trace("Requesting attestation duties for epoch {}", epoch);
    final ScheduledDuties scheduledDuties = scheduledDutiesFactory.get();
    return validatorIndexProvider
        .getValidatorIndices(validators.keySet())
        .thenCompose(
            validatorIndices ->
                requestDuties(epoch, validatorIndices)
                    .thenApply(
                        maybeDuties ->
                            maybeDuties.orElseThrow(
                                () ->
                                    new NodeDataUnavailableException(
                                        "Duties could not be calculated because chain data was not yet available")))
                    .thenCompose(duties -> scheduleAllDuties(scheduledDuties, duties))
                    .thenApply(__ -> scheduledDuties));
  }

  protected abstract SafeFuture<Optional<List<D>>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices);

  protected SafeFuture<Void> scheduleAllDuties(
      final ScheduledDuties scheduledDuties, final List<D> duties) {
    return SafeFuture.allOf(
        duties.stream()
            .map(duty -> scheduleDuties(scheduledDuties, duty))
            .toArray(SafeFuture[]::new));
  }

  protected abstract SafeFuture<Void> scheduleDuties(
      final ScheduledDuties scheduledDuties, final D duty);
}
