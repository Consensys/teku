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

package tech.pegasys.teku.validator.eventadapter;

import static tech.pegasys.teku.util.config.Constants.GENESIS_TIME_RETRY_DELAY_SECONDS;

import com.google.common.base.Suppliers;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class GenesisTimeProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorApiChannel validatorApiChannel;
  private final AsyncRunner asyncRunner;
  private final Supplier<SafeFuture<UInt64>> genesisTime =
      Suppliers.memoize(this::fetchGenesisTime);

  public GenesisTimeProvider(
      final ValidatorApiChannel validatorApiChannel, final AsyncRunner asyncRunner) {
    this.validatorApiChannel = validatorApiChannel;
    this.asyncRunner = asyncRunner;
  }

  public SafeFuture<UInt64> getGenesisTime() {
    return genesisTime.get();
  }

  private SafeFuture<UInt64> fetchGenesisTime() {
    return requestGenesisTime()
        .exceptionallyCompose(
            error -> {
              LOG.error("Failed to retrieve genesis time. Retrying after delay", error);
              return asyncRunner.runAfterDelay(
                  this::fetchGenesisTime, GENESIS_TIME_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            });
  }

  public SafeFuture<UInt64> requestGenesisTime() {
    return validatorApiChannel
        .getGenesisTime()
        .thenCompose(
            maybeGenesisTime ->
                maybeGenesisTime
                    .map(SafeFuture::completedFuture)
                    .orElseGet(
                        () -> {
                          LOG.info("Waiting for genesis time to be known");
                          return asyncRunner.runAfterDelay(
                              this::requestGenesisTime,
                              GENESIS_TIME_RETRY_DELAY_SECONDS,
                              TimeUnit.SECONDS);
                        }));
  }
}
