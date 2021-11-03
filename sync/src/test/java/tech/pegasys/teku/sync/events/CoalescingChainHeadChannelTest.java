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

package tech.pegasys.teku.sync.events;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;

class CoalescingChainHeadChannelTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final ChainHeadChannel delegate = Mockito.mock(ChainHeadChannel.class);

  private final EventLogger eventLogger = Mockito.mock(EventLogger.class);

  private final CoalescingChainHeadChannel channel =
      new CoalescingChainHeadChannel(delegate, eventLogger);

  @Test
  void shouldNotBeSyncingInitially() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();
    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            reorgContext);
  }

  @Test
  void shouldDelegateEventImmediatelyWhenNotSyncing() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(false);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            reorgContext);
  }

  @Test
  void shouldNotDelegateEventsWhileSyncing() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(true);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext);
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldSendLatestHeadUpdateWhenSyncingCompletes() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(true);

    channel.chainHeadUpdated(
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        Optional.empty());

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext);

    verifyNoInteractions(delegate);

    channel.onSyncingChange(false);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            reorgContext);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldNotSendHeadEventWhenSyncingFinishesIfNoneOccurredDuringSyncing() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            reorgContext);

    channel.onSyncingChange(true);
    channel.onSyncingChange(false);

    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldNotResendPreviousCoalescedHeadEventWhenSyncingFinishesASecondTime() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(true);
    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext);
    channel.onSyncingChange(false);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            reorgContext);

    channel.onSyncingChange(true);
    channel.onSyncingChange(false);

    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldNotifyReorg() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();

    final Bytes32 oldBestBlockRoot = dataStructureUtil.randomBytes32();
    final UInt64 oldBestBlockSlot = dataStructureUtil.randomUInt64();
    final Bytes32 oldBestStateRoot = dataStructureUtil.randomBytes32();
    final UInt64 commonAncestorSlot = dataStructureUtil.randomUInt64();
    final Bytes32 commonAncestorRoot = dataStructureUtil.randomBytes32();

    final Optional<ReorgContext> reorgContext =
        Optional.of(
            new ReorgContext(
                oldBestBlockRoot,
                oldBestBlockSlot,
                oldBestStateRoot,
                commonAncestorSlot,
                commonAncestorRoot));
    channel.onSyncingChange(false);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext);

    verify(eventLogger, times(1))
        .reorgEvent(
            oldBestBlockRoot,
            oldBestBlockSlot,
            bestBlockRoot,
            slot,
            commonAncestorRoot,
            commonAncestorSlot);
  }

  @Test
  void shouldUseReorgContextWithEarliestCommonAncestorSlotWhenMultipleEventsReceived() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext1 =
        Optional.of(
            new ReorgContext(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                UInt64.valueOf(100),
                dataStructureUtil.randomBytes32()));
    final Optional<ReorgContext> reorgContext2 =
        Optional.of(
            new ReorgContext(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                UInt64.valueOf(80),
                dataStructureUtil.randomBytes32()));

    channel.onSyncingChange(true);

    channel.chainHeadUpdated(
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        reorgContext2);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        reorgContext1);

    verifyNoInteractions(delegate);

    channel.onSyncingChange(false);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            reorgContext2);
    verifyNoMoreInteractions(delegate);
  }
}
