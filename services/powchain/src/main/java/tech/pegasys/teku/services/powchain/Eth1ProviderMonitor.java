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

package tech.pegasys.teku.services.powchain;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.Eth1ProviderSelector;
import tech.pegasys.teku.pow.MonitorableProvider;
import tech.pegasys.teku.util.config.Constants;

public class Eth1ProviderMonitor {
  private static final Logger LOG = LogManager.getLogger();

  private final Eth1ProviderSelector eth1ProviderSelector;
  private final AsyncRunner asyncRunner;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  public Eth1ProviderMonitor(Eth1ProviderSelector eth1ProviderSelector, AsyncRunner asyncRunner) {
    this.eth1ProviderSelector = eth1ProviderSelector;
    this.asyncRunner = asyncRunner;
  }

  public void start() {
    validate();
  }

  public void stop() {
    stopped.set(true);
  }

  private void validate() {
    if (stopped.get()) {
      return;
    }

    SafeFuture.allOf(
            eth1ProviderSelector.getProviders().stream()
                .filter(MonitorableProvider::needsToBeValidated)
                .map(MonitorableProvider::validate)
                .map(
                    isValidFuture ->
                        isValidFuture.thenPeek(
                            isValid -> {
                              if (isValid) {
                                eth1ProviderSelector.notifyValidationCompletion();
                              }
                            }))
                .toArray(SafeFuture[]::new))
        .alwaysRun(eth1ProviderSelector::notifyValidationCompletion)
        .finish(error -> LOG.error("Unexpected error while validating eth1 endpoints", error));

    asyncRunner
        .runAfterDelay(this::validate, Constants.ETH1_ENDPOINT_MONITOR_SERVICE_POLL_INTERVAL)
        .reportExceptions();
  }
}
