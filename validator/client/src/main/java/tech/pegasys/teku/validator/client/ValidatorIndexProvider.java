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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ValidatorIndexProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final List<BLSPublicKey> unknownValidators;
  private final ValidatorApiChannel validatorApiChannel;
  private final Map<BLSPublicKey, Integer> validatorIndexesByPublicKey = new ConcurrentHashMap<>();

  private final AtomicBoolean requestInProgress = new AtomicBoolean(false);
  private final SafeFuture<Void> firstSuccessfulRequest = new SafeFuture<>();

  public ValidatorIndexProvider(
      final Collection<BLSPublicKey> validators, final ValidatorApiChannel validatorApiChannel) {
    this.unknownValidators = new CopyOnWriteArrayList<>(validators);
    this.validatorApiChannel = validatorApiChannel;
  }

  public void lookupValidators() {
    if (unknownValidators.isEmpty()) {
      return;
    }

    if (!requestInProgress.compareAndSet(false, true)) {
      return;
    }
    LOG.trace("Looking up {} unknown validators", unknownValidators.size());
    validatorApiChannel
        .getValidatorIndices(unknownValidators)
        .thenAccept(
            knownValidators -> {
              logNewValidatorIndices(knownValidators);
              validatorIndexesByPublicKey.putAll(knownValidators);
              unknownValidators.removeAll(knownValidators.keySet());
              firstSuccessfulRequest.complete(null);
            })
        .orTimeout(30, TimeUnit.SECONDS)
        .whenComplete((result, error) -> requestInProgress.set(false))
        .finish(error -> LOG.debug("Failed to load validator indexes.", error));
  }

  private void logNewValidatorIndices(final Map<BLSPublicKey, Integer> knownValidators) {
    if (!knownValidators.isEmpty()) {
      LOG.debug(
          "Discovered new indices for validators: {}",
          () ->
              knownValidators.entrySet().stream()
                  .map(entry -> entry.getKey().toAbbreviatedString() + "=" + entry.getValue())
                  .collect(joining(", ")));
    }
  }

  public Optional<Integer> getValidatorIndex(final BLSPublicKey publicKey) {
    return Optional.ofNullable(validatorIndexesByPublicKey.get(publicKey));
  }

  public SafeFuture<Collection<Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    // Wait for at least one successful load of validator indices before attempting to read
    return firstSuccessfulRequest.thenApply(
        __ ->
            publicKeys.stream().flatMap(key -> getValidatorIndex(key).stream()).collect(toList()));
  }
}
