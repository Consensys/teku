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

package tech.pegasys.teku.statetransition.synccommittee;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.util.config.Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class SyncCommitteeCurrentSlotUtilTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  private final Spec spec =
      TestSpecFactory.createAltair(
          SpecConfigLoader.loadConfig(
              "minimal",
              phase0Builder ->
                  phase0Builder.altairBuilder(
                      altairBuilder ->
                          altairBuilder.syncCommitteeSize(16).altairForkEpoch(UInt64.ZERO))));
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create().specProvider(spec).numberOfValidators(17).build();
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final SyncCommitteeCurrentSlotUtil slotUtil =
      new SyncCommitteeCurrentSlotUtil(recentChainData, spec, timeProvider);

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void isForCurrentSlot_shouldNotUnderflow() {
    assertThat(slotUtil.isForCurrentSlot(UInt64.ZERO)).isTrue();
  }

  @Test
  void isForCurrentSlot_shouldRejectOutsideLowerBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 slotStartTimeMillis =
        spec.getSlotStartTime(slot, recentChainData.getGenesisTime()).times(1000);
    timeProvider.advanceTimeByMillis(
        slotStartTimeMillis.minus(MAXIMUM_GOSSIP_CLOCK_DISPARITY).decrement().longValue());
    assertThat(slotUtil.isForCurrentSlot(slot)).isFalse();
  }

  @Test
  void isForCurrentSlot_shouldAcceptLowerBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 slotStartTimeMillis =
        spec.getSlotStartTime(slot, recentChainData.getGenesisTime()).times(1000);
    timeProvider.advanceTimeByMillis(
        slotStartTimeMillis.minus(MAXIMUM_GOSSIP_CLOCK_DISPARITY).longValue());
    assertThat(slotUtil.isForCurrentSlot(slot)).isTrue();
  }

  @Test
  void isForCurrentSlot_shouldAcceptUpperBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 nextSlotStartTimeMillis =
        spec.getSlotStartTime(slot.increment(), recentChainData.getGenesisTime()).times(1000);
    timeProvider.advanceTimeByMillis(
        nextSlotStartTimeMillis.plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY).longValue());
    assertThat(slotUtil.isForCurrentSlot(slot)).isTrue();
  }

  @Test
  void isForCurrentSlot_shouldRecjectOutsideUpperBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 nextSlotStartTimeMillis =
        spec.getSlotStartTime(slot.increment(), recentChainData.getGenesisTime()).times(1000);
    timeProvider.advanceTimeByMillis(
        nextSlotStartTimeMillis.plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY).increment().longValue());
    assertThat(slotUtil.isForCurrentSlot(slot)).isFalse();
  }
}
