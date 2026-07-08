/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainHead;

/** Glue between {@code ForkChoice.onTick} and the fast confirmation side runner. */
public final class ForkChoiceFastConfirmation {
  private static final Logger LOG = LogManager.getLogger();

  private ForkChoiceFastConfirmation() {}

  /**
   * Runs the FCR slot-start work after deferred attestations have been applied.
   *
   * <p>FCR is defined from the post-deferred-attestation head at slot start, so this deliberately
   * triggers the extra early-slot fork-choice calculation supplied by {@code processHead}. Only the
   * head snapshot is taken on the fork-choice thread; everything else (epoch-boundary flags,
   * greatest unrealized justified checkpoint, source states, and the confirmation computation) is
   * handed to the fast confirmation runner via {@link FastConfirmationTracker#onSlot}.
   */
  public static void processForSlot(
      final FastConfirmationTracker fastConfirmationTracker,
      final UInt64 currentSlot,
      final SafeFuture<Void> deferredAttestationsFuture,
      final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead) {
    deferredAttestationsFuture
        .thenCompose(__ -> processHead.apply(currentSlot))
        .thenCompose(
            maybeHead ->
                maybeHead
                    .map(head -> fastConfirmationTracker.onSlot(currentSlot, head.getRoot()))
                    .orElse(SafeFuture.COMPLETE))
        .finish(error -> LOG.error("Fast confirmation update failed", error));
  }
}
