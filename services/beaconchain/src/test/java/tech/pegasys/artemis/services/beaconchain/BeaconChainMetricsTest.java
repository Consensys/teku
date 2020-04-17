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

package tech.pegasys.artemis.services.beaconchain;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.blocks.NodeSlot;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.client.RecentChainData;

class BeaconChainMetricsTest {
  private static final UnsignedLong NODE_SLOT_VALUE = UnsignedLong.valueOf(100L);
  private final Bytes32 root =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFFFF");
  private final Bytes32 root2 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFF7F");
  private final Bytes32 root3 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24e0000000000000080");
  private final Checkpoint checkpoint = new Checkpoint(NODE_SLOT_VALUE, root);

  private static BeaconBlockAndState blockAndState = mock(BeaconBlockAndState.class);
  private BeaconState state = mock(BeaconState.class);

  private MetricsSystem metricsSystem = mock(MetricsSystem.class);
  private final NodeSlot nodeSlot = new NodeSlot(NODE_SLOT_VALUE);

  private final RecentChainData recentChainData = mock(RecentChainData.class);

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
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    assertThat(NODE_SLOT_VALUE.longValue()).isEqualTo(metrics.getCurrentSlotValue());
  }

  @Test
  void getHeadSlotValue_shouldSupplyValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestSlot()).thenReturn(ONE);

    assertThat(1L).isEqualTo(metrics.getHeadSlotValue());
    verify(recentChainData).isPreGenesis();
    verify(recentChainData).getBestSlot();
  }

  @Test
  void getHeadSlotValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(0L).isEqualTo(metrics.getHeadSlotValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getFinalizedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(0L).isEqualTo(metrics.getFinalizedEpochValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getFinalizedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getFinalizedEpoch()).thenReturn(ONE);

    assertThat(1L).isEqualTo(metrics.getFinalizedEpochValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getHeadRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(0L).isEqualTo(metrics.getHeadRootValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getHeadRootValue_shouldReturnValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(root));

    assertThat(-1L).isEqualTo(metrics.getHeadRootValue());
    verify(recentChainData).getBestBlockRoot();
  }

  @Test
  void getFinalizedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);
    when(recentChainData.getBestBlockAndState()).thenCallRealMethod();

    assertThat(0L).isEqualTo(metrics.getFinalizedRootValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getFinalizedRootValue_shouldReturnValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getFinalized_checkpoint()).thenReturn(checkpoint);

    assertThat(-1L).isEqualTo(metrics.getFinalizedRootValue());
    verify(state).getFinalized_checkpoint();
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);
    when(recentChainData.getBestBlockAndState()).thenCallRealMethod();

    assertThat(0L).isEqualTo(metrics.getPreviousJustifiedEpochValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getPrevious_justified_checkpoint()).thenReturn(checkpoint);

    assertThat(NODE_SLOT_VALUE.longValue()).isEqualTo(metrics.getPreviousJustifiedEpochValue());
    verify(state).getPrevious_justified_checkpoint();
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);
    when(recentChainData.getBestBlockAndState()).thenCallRealMethod();

    assertThat(0L).isEqualTo(metrics.getPreviousJustifiedRootValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getPrevious_justified_checkpoint()).thenReturn(checkpoint);

    assertThat(-1L).isEqualTo(metrics.getPreviousJustifiedRootValue());
    verify(state).getPrevious_justified_checkpoint();
  }

  @Test
  void getJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);
    when(recentChainData.getBestBlockAndState()).thenCallRealMethod();

    assertThat(0L).isEqualTo(metrics.getJustifiedRootValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.getBestBlockAndState()).thenReturn(Optional.of(blockAndState));
    when(blockAndState.getState()).thenReturn(state);
    when(state.getCurrent_justified_checkpoint()).thenReturn(new Checkpoint(NODE_SLOT_VALUE, root));

    assertThat(-1L).isEqualTo(metrics.getJustifiedRootValue());
    verify(state).getCurrent_justified_checkpoint();
  }

  @Test
  void getJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(0L).isEqualTo(metrics.getJustifiedEpochValue());
    verify(recentChainData).isPreGenesis();
  }

  @Test
  void getJustifiedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    BeaconChainMetrics metrics = new BeaconChainMetrics(metricsSystem, recentChainData, nodeSlot);
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestJustifiedEpoch()).thenReturn(ONE);

    assertThat(1L).isEqualTo(metrics.getJustifiedEpochValue());
    verify(recentChainData).isPreGenesis();
  }
}
