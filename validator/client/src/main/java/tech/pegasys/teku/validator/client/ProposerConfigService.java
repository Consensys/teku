/*
 * Copyright 2022 ConsenSys AG.
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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

public class ProposerConfigService extends Service {
  private static final Logger LOG = LogManager.getLogger();
  static final Duration DEFAULT_REFRESH_RATE = Duration.ofMinutes(1);

  private final Duration refreshRate;
  private final AsyncRunner asyncRunner;
  private Optional<Cancellable> cancellable = Optional.empty();
  private final AtomicBoolean running = new AtomicBoolean(false);

  public ProposerConfigService(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    this.refreshRate = DEFAULT_REFRESH_RATE;
  }

  @Override
  protected SafeFuture<?> doStart() {
    cancellable =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::loadProposerConfig,
                refreshRate,
                error -> LOG.error("Failed to refresh proposer configuration", error)));
    // Run immediately on start
    loadProposerConfig();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    cancellable.ifPresent(Cancellable::cancel);
    cancellable = Optional.empty();
    return SafeFuture.COMPLETE;
  }

  private void loadProposerConfig() {
    if (running.compareAndSet(false, true)) {}
  }
}
