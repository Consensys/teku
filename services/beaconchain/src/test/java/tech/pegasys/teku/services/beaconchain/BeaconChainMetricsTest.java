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

package tech.pegasys.teku.services.beaconchain;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

class BeaconChainMetricsTest {
  private static final UnsignedLong NODE_SLOT_VALUE = UnsignedLong.valueOf(100L);
  private final Bytes32 root =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFFFF");
  private final Bytes32 root2 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFF7F");
  private final Bytes32 root3 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24e0000000000000080");
  private final Checkpoint checkpoint = new Checkpoint(NODE_SLOT_VALUE, root);

  private static final BeaconBlockAndState blockAndState = mock(BeaconBlockAndState.class);
  private final BeaconState state = mock(BeaconState.class);

  private final NodeSlot nodeSlot = new NodeSlot(NODE_SLOT_VALUE);

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final RecentChainData preGenesisChainData =
      MemoryOnlyRecentChainData.create(mock(EventBus.class));

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @BeforeEach
  void setUp() {
    new BeaconChainMetrics(recentChainData, nodeSlot, metricsSystem);
  }

  @Test
  void getLongFromRoot_shouldParseNegativeOne() {
    assertThat(-1L).isEqualTo(BeaconChainMetrics.getLongFromRoot(root));
  }

  @Test
  void getLongFromRoot_shouldParseMaxLong() {
    assertThat(Long.MAX_VALUE).isEqualTo(BeaconChainMetrics.getLongFromRoot(root2));
  }

  @Test
  void getLongFromRoot_shouldParseMinLong() {
    assertThat(Long.MIN_VALUE).isEqualTo(BeaconChainMetrics.getLongFromRoot(root3));
  }

  @Test
  void getCurrentSlotValue_shouldReturnCurrentSlot() {
    assertThat(metricsSystem.getGauge(BEACON, "slot").getValue())
        .isEqualTo(NODE_SLOT_VALUE.longValue());
  }

  @Test
  void getHeadSlotValue_shouldSupplyValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestSlot()).thenReturn(ONE);

    assertThat(metricsSystem.getGauge(BEACON, "head_slot").getValue()).isEqualTo(1L);
  }

  @Test
  void getHeadSlotValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "head_slot").getValue()).isZero();
  }

  @Test
  void getFinalizedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "finalized_epoch").getValue()).isZero();
  }

  @Test
  void getFinalizedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getFinalizedEpoch()).thenReturn(ONE);

    assertThat(metricsSystem.getGauge(BEACON, "finalized_epoch").getValue()).isEqualTo(1);
  }

  @Test
  void getHeadRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "head_root").getValue()).isZero();
  }

  @Test
  void getHeadRootValue_shouldReturnValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(root));

    assertThat(metricsSystem.getGauge(BEACON, "head_root").getValue()).isEqualTo(-1);
  }

  @Test
  void getFinalizedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "finalized_root").getValue()).isZero();
  }

  @Test
  void getFinalizedRootValue_shouldReturnValueWhenStoreIsPresent() {
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getFinalized_checkpoint()).thenReturn(checkpoint);

    assertThat(metricsSystem.getGauge(BEACON, "finalized_root").getValue()).isEqualTo(-1);
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_epoch").getValue()).isZero();
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getPrevious_justified_checkpoint()).thenReturn(checkpoint);

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_epoch").getValue())
        .isEqualTo(NODE_SLOT_VALUE.longValue());
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_root").getValue()).isZero();
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getPrevious_justified_checkpoint()).thenReturn(checkpoint);

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_root").getValue()).isEqualTo(-1);
  }

  @Test
  void getJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_root").getValue()).isZero();
  }

  @Test
  void getJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getCurrent_justified_checkpoint()).thenReturn(new Checkpoint(NODE_SLOT_VALUE, root));

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_root").getValue()).isEqualTo(-1);
  }

  @Test
  void getJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_epoch").getValue()).isZero();
  }

  @Test
  void getJustifiedEpochValue_shouldReturnValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestJustifiedEpoch()).thenReturn(ONE);

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_epoch").getValue()).isEqualTo(1);
  }

  @Test
  void getCurrentEpochValue_shouldReturnValueWhenNodeSlotIsSet() {
    final long epochAtSlot = nodeSlot.longValue() / SLOTS_PER_EPOCH;
    assertThat(metricsSystem.getGauge(BEACON, "epoch").getValue()).isEqualTo(epochAtSlot);
  }
}
