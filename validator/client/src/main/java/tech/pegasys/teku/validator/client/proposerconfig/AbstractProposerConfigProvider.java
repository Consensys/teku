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

package tech.pegasys.teku.validator.client.proposerconfig;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.client.ProposerConfig;
import tech.pegasys.teku.validator.client.proposerconfig.loader.ProposerConfigLoader;

public abstract class AbstractProposerConfigProvider implements ProposerConfigProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final boolean refresh;
  private final AsyncRunner asyncRunner;
  protected final ProposerConfigLoader proposerConfigLoader;
  private Optional<ProposerConfig> lastProposerConfig = Optional.empty();
  private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

  AbstractProposerConfigProvider(
      final AsyncRunner asyncRunner,
      final boolean refresh,
      final ProposerConfigLoader proposerConfigLoader) {
    this.asyncRunner = asyncRunner;
    this.refresh = refresh;
    this.proposerConfigLoader = proposerConfigLoader;
  }

  @Override
  public SafeFuture<Optional<ProposerConfig>> getProposerConfig() {
    if (lastProposerConfig.isPresent() && !refresh) {
      return SafeFuture.completedFuture(lastProposerConfig);
    }

    if (!requestInProgress.compareAndSet(false, true)) {
      if (lastProposerConfig.isPresent()) {
        LOG.warn("A proposer config load is in progress, providing last loaded config");
        return SafeFuture.completedFuture(lastProposerConfig);
      }
      return SafeFuture.failedFuture(
          new RuntimeException(
              "A proposer config load is in progress and there is no previously loaded config"));
    }

    return asyncRunner
        .runAsync(
            () -> {
              lastProposerConfig = Optional.of(internalGetProposerConfig());
              return lastProposerConfig;
            })
        .orTimeout(30, TimeUnit.SECONDS)
        .exceptionally(
            throwable -> {
              if (lastProposerConfig.isPresent()) {
                LOG.warn("An error occurred while obtaining config, providing last loaded config");
                return lastProposerConfig;
              }
              throw new RuntimeException(
                  "An error occurred while obtaining config and there is no previously loaded config",
                  throwable);
            })
        .alwaysRun(() -> requestInProgress.set(false));
  }

  protected abstract ProposerConfig internalGetProposerConfig();
}
