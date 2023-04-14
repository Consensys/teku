/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl.GAUGE_BLOB_SIDECARS_LABEL;
import static tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl.GAUGE_BLOB_SIDECARS_TRACKERS_LABEL;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarPoolTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 historicalTolerance = UInt64.valueOf(5);
  private final UInt64 futureTolerance = UInt64.valueOf(2);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final int maxItems = 15;
  private final BlobSidecarPoolImpl blobSidecarPool =
      new PoolFactory(metricsSystem)
          .createPoolForBlobSidecars(
              spec,
              timeProvider,
              asyncRunner,
              recentChainData,
              historicalTolerance,
              futureTolerance,
              maxItems);

  private UInt64 currentSlot = historicalTolerance.times(2);
  private final List<Bytes32> requiredRootEvents = new ArrayList<>();
  private final List<Bytes32> requiredRootDroppedEvents = new ArrayList<>();
  private final List<BlobIdentifier> requiredBlobSidecarEvents = new ArrayList<>();
  private final List<BlobIdentifier> requiredBlobSidecarDroppedEvents = new ArrayList<>();

  @BeforeEach
  public void setup() {
    // Set up slot
    blobSidecarPool.subscribeRequiredBlockRoot(requiredRootEvents::add);
    blobSidecarPool.subscribeRequiredBlockRootDropped(requiredRootDroppedEvents::add);
    blobSidecarPool.subscribeRequiredBlobSidecar(requiredBlobSidecarEvents::add);
    blobSidecarPool.subscribeRequiredBlobSidecarDropped(requiredBlobSidecarDroppedEvents::add);
    setSlot(currentSlot);
  }

  private void setSlot(final UInt64 slot) {
    currentSlot = slot;
    blobSidecarPool.onSlot(slot);
    when(recentChainData.computeTimeAtSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  public void onNewBlock_addTrackerWithBlockSetWhenSlotIsCurrentSlot() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blobSidecarPool.onNewBlock(block);

    assertThat(blobSidecarPool.containsBlock(block)).isTrue();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();

    assertBlobSidecarsGauge(0);
    assertBlobSidecarsTrackersGauge(1);
  }

  @Test
  public void onNewBlobSidecar_addTrackerWithBlockSetWhenSlotIsCurrentSlot() {
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().slot(currentSlot).build();

    blobSidecarPool.onNewBlobSidecar(blobSidecar, false);

    assertThat(blobSidecarPool.containsBlobSidecar(blobIdentifierFromBlobSidecar(blobSidecar)))
        .isTrue();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();

    assertBlobSidecarsGauge(1);
    assertBlobSidecarsTrackersGauge(1);
  }

  @Test
  public void shouldApplyIgnoreForBlock() {
    final UInt64 slot = currentSlot.plus(futureTolerance).plus(UInt64.ONE);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());

    blobSidecarPool.onNewBlock(block);

    assertThat(blobSidecarPool.containsBlock(block)).isFalse();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();

    assertBlobSidecarsGauge(0);
    assertBlobSidecarsTrackersGauge(0);
  }

  @Test
  public void shouldApplyIgnoreForBlobSidecar() {
    final UInt64 slot = currentSlot.plus(futureTolerance).plus(UInt64.ONE);
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().slot(slot).build();

    blobSidecarPool.onNewBlobSidecar(blobSidecar, false);

    assertThat(blobSidecarPool.containsBlobSidecar(blobIdentifierFromBlobSidecar(blobSidecar)))
        .isFalse();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();

    assertBlobSidecarsGauge(0);
    assertBlobSidecarsTrackersGauge(0);
  }

  @Test
  public void add_moreThanMaxItems() {
    for (int i = 0; i < maxItems * 2; i++) {
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
      blobSidecarPool.onNewBlock(block);

      final int expectedSize = Math.min(maxItems, i + 1);
      assertThat(blobSidecarPool.containsBlock(block)).isTrue();
      assertThat(blobSidecarPool.getTotalBlobSidecarsTrackers()).isEqualTo(expectedSize);
      assertBlobSidecarsTrackersGauge(expectedSize);
    }

    // Final sanity check
    assertThat(blobSidecarPool.getTotalBlobSidecarsTrackers()).isEqualTo(maxItems);
    assertBlobSidecarsTrackersGauge(maxItems);

    assertBlobSidecarsGauge(0);
  }

  private static BlobIdentifier blobIdentifierFromBlobSidecar(final BlobSidecar blobSidecar) {
    return new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
  }

  private void assertBlobSidecarsGauge(final int count) {
    assertThat(
            metricsSystem
                .getLabelledGauge(TekuMetricCategory.BEACON, "pending_pool_size")
                .getValue(GAUGE_BLOB_SIDECARS_LABEL))
        .isEqualTo(OptionalDouble.of(count));
  }

  private void assertBlobSidecarsTrackersGauge(final int count) {
    assertThat(
            metricsSystem
                .getLabelledGauge(TekuMetricCategory.BEACON, "pending_pool_size")
                .getValue(GAUGE_BLOB_SIDECARS_TRACKERS_LABEL))
        .isEqualTo(OptionalDouble.of(count));
  }
}
