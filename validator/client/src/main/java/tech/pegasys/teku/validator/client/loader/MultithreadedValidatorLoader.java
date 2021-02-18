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

package tech.pegasys.teku.validator.client.loader;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;

/**
 * Loads validators in parallel while also limiting the number of keystores being decrypted
 * simultaneously. This is required because decrypting scrypt keystores uses a significant amount of
 * memory. If a simple `parallelStream` was used and the machine had a large number of CPUs the
 * available memory would be exhausted resulting in a crash with `OutOfMemoryError`.
 *
 * <p>Progress is reported to the logs to keep the user informed as loading a large number of keys
 * can be slow.
 */
public class MultithreadedValidatorLoader {

  public static Map<BLSPublicKey, Validator> loadValidators(
      final Map<BLSPublicKey, ValidatorProvider> providers,
      final GraffitiProvider graffitiProvider) {
    final int totalValidatorCount = providers.size();
    STATUS_LOG.loadingValidators(totalValidatorCount);

    final ExecutorService executorService =
        Executors.newFixedThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));
    try {
      final AtomicInteger numberOfLoadedKeys = new AtomicInteger(0);
      final List<Future<Validator>> futures =
          providers.values().stream()
              .map(
                  provider ->
                      executorService.submit(
                          () -> {
                            final Validator validator =
                                new Validator(
                                    provider.getPublicKey(),
                                    provider.createSigner(),
                                    graffitiProvider);
                            int loadedValidatorCount = numberOfLoadedKeys.incrementAndGet();
                            if (loadedValidatorCount % 10 == 0) {
                              STATUS_LOG.atLoadedValidatorNumber(
                                  loadedValidatorCount, totalValidatorCount);
                            }
                            return validator;
                          }))
              .collect(toList());

      final Map<BLSPublicKey, Validator> validators = new HashMap<>();
      for (Future<Validator> future : futures) {
        final Validator validator = future.get();
        validators.put(validator.getPublicKey(), validator);
      }

      STATUS_LOG.validatorsInitialised(
          validators.values().stream()
              .map(Validator::getPublicKey)
              .map(BLSPublicKey::toAbbreviatedString)
              .collect(Collectors.toList()));

      return validators;
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while attempting to load validator key files", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException("Unable to load validator key files", e);
    } finally {
      executorService.shutdownNow();
    }
  }
}
