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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.client.ChainHead;

class ForkChoiceFastConfirmationTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final FastConfirmationTracker tracker = mock(FastConfirmationTracker.class);
  // Inject the stub runner so the segment timeout can be driven deterministically.
  private final ForkChoiceFastConfirmation forkChoiceFastConfirmation =
      new ForkChoiceFastConfirmation(spec, tracker, asyncRunner);

  private final Duration slotDuration = Duration.ofMillis(spec.getSlotDurationMillis(UInt64.ONE));

  private final List<UInt64> onSlotInvocations = new ArrayList<>();

  // Records the order of events so tests can assert the fcU is sent once per slot, after onSlot.
  private final List<String> events = new ArrayList<>();
  private final Supplier<SafeFuture<Void>> sendForkChoiceUpdated =
      () -> {
        events.add("fcU");
        return SafeFuture.COMPLETE;
      };

  ForkChoiceFastConfirmationTest() {
    when(tracker.onSlot(any(), any()))
        .thenAnswer(
            invocation -> {
              final UInt64 slot = invocation.getArgument(0);
              onSlotInvocations.add(slot);
              events.add("onSlot:" + slot);
              return SafeFuture.COMPLETE;
            });
  }

  @Test
  void shouldInvokeOnSlotInSlotOrderEvenWhenAnEarlierSlotProcessHeadIsSlow() {
    // Slot 1's processHead is slow (pending); slot 2's is immediate. Without chaining, slot 2 would
    // reach onSlot first and slot 1 would then be dropped as stale by the tracker.
    final SafeFuture<Optional<ChainHead>> slowHead = new SafeFuture<>();
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot ->
            slot.equals(UInt64.ONE)
                ? slowHead
                : SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random())));

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);
    forkChoiceFastConfirmation.processForSlot(
        UInt64.valueOf(2), SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // Slot 1 is still blocked on its processHead, so slot 2 must not have jumped ahead.
    assertThat(onSlotInvocations).isEmpty();

    // Slot 1 completes before its timeout, so both pipelines drain strictly in slot order.
    slowHead.complete(Optional.of(chainHead(Bytes32.random())));

    assertThat(onSlotInvocations).containsExactly(UInt64.ONE, UInt64.valueOf(2));
    // Each slot sends exactly one fcU, after its onSlot.
    assertThat(events).containsExactly("onSlot:1", "fcU", "onSlot:2", "fcU");
  }

  @Test
  void shouldTimeoutAndNotWedgeLaterSlotsWhenAProcessHeadHangs() {
    // Slot 1's processHead never completes; slot 2's is immediate.
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot ->
            slot.equals(UInt64.ONE)
                ? new SafeFuture<>()
                : SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random())));

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);
    forkChoiceFastConfirmation.processForSlot(
        UInt64.valueOf(2), SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // Nothing runs while slot 1 hangs and its timeout has not fired.
    assertThat(onSlotInvocations).isEmpty();

    // Advance past the segment timeout and fire it: slot 1 is abandoned and slot 2 proceeds.
    timeProvider.advanceTimeBy(slotDuration.plusMillis(1));
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(onSlotInvocations).containsExactly(UInt64.valueOf(2));
    // Only slot 2 ran, so only slot 2 sent an fcU; the abandoned slot 1 sent none.
    assertThat(events).containsExactly("onSlot:2", "fcU");
  }

  @Test
  void shouldContinueProcessingLaterSlotsAfterAnEarlierSlotFails() {
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot ->
            slot.equals(UInt64.ONE)
                ? SafeFuture.failedFuture(new IllegalStateException("processHead failed"))
                : SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random())));

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);
    forkChoiceFastConfirmation.processForSlot(
        UInt64.valueOf(2), SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // Slot 1 failed (no onSlot), but the chain stayed alive and slot 2 still ran.
    assertThat(onSlotInvocations).containsExactly(UInt64.valueOf(2));
  }

  @Test
  void shouldSkipOnSlotWhenNoHeadIsAvailable() {
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot -> SafeFuture.completedFuture(Optional.empty());

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // No head, so neither onSlot nor the fcU runs for this slot.
    assertThat(onSlotInvocations).isEmpty();
    assertThat(events).isEmpty();
  }

  private ChainHead chainHead(final Bytes32 root) {
    final ChainHead chainHead = mock(ChainHead.class);
    when(chainHead.getRoot()).thenReturn(root);
    return chainHead;
  }
}
