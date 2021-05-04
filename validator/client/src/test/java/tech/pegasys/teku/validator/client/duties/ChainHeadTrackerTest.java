/*
 * Copyright 2021 ConsenSys AG.
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

import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.duties.synccommittee.ChainHeadTracker;

class ChainHeadTrackerTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final ChainHeadTracker tracker = new ChainHeadTracker();

  @Test
  void shouldReturnEmptyWhenNoUpdatesReceived() {
    assertThatSafeFuture(tracker.getCurrentChainHead(UInt64.MAX_VALUE))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void shouldReturnLatestHeadWhenItIsAtRequestedSlot() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();
    updateHead(slot, headBlockRoot);
    assertThatSafeFuture(tracker.getCurrentChainHead(slot))
        .isCompletedWithOptionalContaining(headBlockRoot);
  }

  @Test
  void shouldReturnLatestHeadWhenItIsBeforeRequestedSlot() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();
    updateHead(slot, headBlockRoot);
    assertThatSafeFuture(tracker.getCurrentChainHead(slot.plus(1)))
        .isCompletedWithOptionalContaining(headBlockRoot);
  }

  @Test
  void shouldReturnEmptyWhenHeadIsAfterRequestedSlot() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 headBlockRoot = dataStructureUtil.randomBytes32();
    updateHead(slot, headBlockRoot);
    assertThatSafeFuture(tracker.getCurrentChainHead(slot.minus(1))).isCompletedWithEmptyOptional();
  }

  private void updateHead(final UInt64 slot, final Bytes32 headBlockRoot) {
    tracker.onHeadUpdate(
        slot, dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32(), headBlockRoot);
  }
}
