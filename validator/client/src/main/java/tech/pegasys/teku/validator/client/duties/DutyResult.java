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

package tech.pegasys.teku.validator.client.duties;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.NodeSyncingException;

public class DutyResult {
  public static final DutyResult NO_OP = new DutyResult(0, 0, emptySet(), emptyList());
  private final int successCount;
  private final int nodeSyncingCount;
  private final Set<Bytes32> roots;
  private final List<Throwable> errors;

  private DutyResult(
      final int successCount,
      final int nodeSyncingCount,
      final Set<Bytes32> roots,
      final List<Throwable> errors) {
    this.successCount = successCount;
    this.nodeSyncingCount = nodeSyncingCount;
    this.roots = roots;
    this.errors = errors;
  }

  public static DutyResult success(final Bytes32 result) {
    return new DutyResult(1, 0, singleton(result), emptyList());
  }

  public static DutyResult forError(final Throwable error) {
    // Not using getRootCause here because we only want to unwrap CompletionException
    Throwable cause = error;
    while (cause instanceof CompletionException && cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (cause instanceof NodeSyncingException) {
      return new DutyResult(0, 1, emptySet(), emptyList());
    } else {
      return new DutyResult(0, 0, emptySet(), List.of(cause));
    }
  }

  public static SafeFuture<DutyResult> combine(final List<SafeFuture<DutyResult>> futures) {
    return SafeFuture.allOf(futures.toArray(SafeFuture[]::new))
        .handle(
            (ok, error) ->
                futures.stream()
                    .map(future -> future.exceptionally(DutyResult::forError).getNow(null))
                    .reduce(DutyResult::combine)
                    .orElse(NO_OP));
  }

  public DutyResult combine(final DutyResult other) {
    final int combinedSuccessCount = this.successCount + other.successCount;
    final int combinedSyncingCount = this.nodeSyncingCount + other.nodeSyncingCount;

    final Set<Bytes32> combinedRoots = new HashSet<>(this.roots);
    combinedRoots.addAll(other.roots);

    final List<Throwable> combinedErrors = new ArrayList<>(this.errors);
    combinedErrors.addAll(other.errors);

    return new DutyResult(
        combinedSuccessCount, combinedSyncingCount, combinedRoots, combinedErrors);
  }

  public void report(
      final String producedType,
      final UInt64 slot,
      final Optional<String> validatorKey,
      final ValidatorLogger logger) {
    if (successCount > 0) {
      logger.dutyCompleted(producedType, slot, successCount, roots);
    }
    if (nodeSyncingCount > 0) {
      logger.dutySkippedWhileSyncing(producedType, slot, nodeSyncingCount);
    }
    errors.forEach(error -> logger.dutyFailed(producedType, slot, validatorKey, error));
  }

  @Override
  public String toString() {
    return "DutyResult{"
        + "successCount="
        + successCount
        + ", nodeSyncingCount="
        + nodeSyncingCount
        + ", roots="
        + roots
        + ", errors="
        + errors
        + '}';
  }
}
