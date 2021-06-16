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

import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class RetryingDutyLoader<S extends ScheduledDuties> implements DutyLoader<S> {
  private static final Logger LOG = LogManager.getLogger();

  private final DutyLoader<S> delegate;
  private final AsyncRunner asyncRunner;

  public RetryingDutyLoader(final AsyncRunner asyncRunner, final DutyLoader<S> delegate) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<Optional<S>> loadDutiesForEpoch(final UInt64 epoch) {
    final SafeFuture<Optional<S>> duties = new SafeFuture<>();
    requestDuties(epoch, duties).propagateTo(duties);
    return duties;
  }

  private SafeFuture<Optional<S>> requestDuties(
      final UInt64 epoch, final SafeFuture<Optional<S>> cancellable) {
    LOG.trace("Request duties for epoch {}", epoch);
    return delegate
        .loadDutiesForEpoch(epoch)
        .exceptionallyCompose(
            error -> {
              if (cancellable.isCancelled()) {
                LOG.debug("Request for duties for epoch {} cancelled.", epoch);
                return SafeFuture.failedFuture(error);
              }
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof NodeSyncingException) {
                LOG.debug(
                    "Unable to schedule duties for epoch {} because node was syncing. Retrying.",
                    epoch);
              } else if (rootCause instanceof NodeDataUnavailableException) {
                LOG.debug(
                    "Unable to schedule duties for epoch {} because required state was not yet available. Retrying.",
                    epoch);
              } else {
                LOG.error(
                    "Failed to request validator duties for epoch "
                        + epoch
                        + ". Retrying after delay.",
                    error);
              }
              // Short delay before retrying as loading duties is very time sensitive
              return asyncRunner.runAfterDelay(
                  () -> requestDuties(epoch, cancellable), Duration.ofSeconds(1));
            });
  }
}
