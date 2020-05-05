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
import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class RetryingDutyLoader implements DutyLoader {
  private static final Logger LOG = LogManager.getLogger();

  private final DutyLoader delegate;
  private final AsyncRunner asyncRunner;

  public RetryingDutyLoader(final AsyncRunner asyncRunner, final DutyLoader delegate) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<ScheduledDuties> loadDutiesForEpoch(final UnsignedLong epoch) {
    final SafeFuture<ScheduledDuties> duties = new SafeFuture<>();
    requestDuties(epoch, duties).propagateTo(duties);
    return duties;
  }

  private SafeFuture<ScheduledDuties> requestDuties(
      final UnsignedLong epoch, final SafeFuture<ScheduledDuties> cancellable) {
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
              return asyncRunner.runAfterDelay(
                  () -> requestDuties(epoch, cancellable), 5, TimeUnit.SECONDS);
            });
  }
}
