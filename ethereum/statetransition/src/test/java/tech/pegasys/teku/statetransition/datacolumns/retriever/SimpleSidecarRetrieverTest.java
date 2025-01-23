/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolverStub;

@SuppressWarnings({"unused", "JavaCase"})
public class SimpleSidecarRetrieverTest {
  final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);
  final DataColumnPeerSearcherStub dataColumnPeerSearcherStub = new DataColumnPeerSearcherStub();
  final TestPeerManager testPeerManager = new TestPeerManager();

  final Spec spec = TestSpecFactory.createMinimalFulu();
  final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  final MiscHelpersFulu miscHelpers =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  final int columnCount = config.getNumberOfColumns();
  final KZG kzg = KZG.getInstance(false);

  final DasPeerCustodyCountSupplierStub custodyCountSupplier =
      new DasPeerCustodyCountSupplierStub(config.getCustodyRequirement());

  final Duration retrieverRound = Duration.ofSeconds(1);
  final SimpleSidecarRetriever simpleSidecarRetriever =
      new SimpleSidecarRetriever(
          spec,
          testPeerManager,
          dataColumnPeerSearcherStub,
          custodyCountSupplier,
          testPeerManager,
          stubAsyncRunner,
          retrieverRound);

  UInt64 columnIndex = UInt64.valueOf(1);

  Iterator<UInt256> custodyNodeIds = craftNodeIdsCustodyOf(columnIndex).iterator();
  Iterator<UInt256> nonCustodyNodeIds = craftNodeIdsNotCustodyOf(columnIndex).iterator();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);

  public SimpleSidecarRetrieverTest() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  private SignedBeaconBlock createSigned(final BeaconBlock block) {
    return dataStructureUtil.signedBlock(block);
  }

  private DataColumnSlotAndIdentifier createId(final BeaconBlock block, final int colIdx) {
    return new DataColumnSlotAndIdentifier(
        block.getSlot(), block.getRoot(), UInt64.valueOf(colIdx));
  }

  List<UInt64> nodeCustodyColumns(final UInt256 nodeId) {
    return miscHelpers.computeCustodyColumnIndexes(
        nodeId, custodyCountSupplier.getCustodyGroupCountForPeer(nodeId));
  }

  Stream<UInt256> craftNodeIds() {
    return IntStream.iterate(0, i -> i + 1).mapToObj(UInt256::valueOf);
  }

  Stream<UInt256> craftNodeIdsCustodyOf(final UInt64 custodyColumn) {
    return craftNodeIds().filter(nodeId -> nodeCustodyColumns(nodeId).contains(custodyColumn));
  }

  Stream<UInt256> craftNodeIdsNotCustodyOf(final UInt64 custodyColumn) {
    return craftNodeIds().filter(nodeId -> !nodeCustodyColumns(nodeId).contains(custodyColumn));
  }

  private void advanceTimeGradually(final Duration delta) {
    for (int i = 0; i < delta.toMillis(); i++) {
      stubTimeProvider.advanceTimeBy(Duration.ofMillis(1));
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
  }

  @Test
  void sanityTest() {
    TestPeer custodyPeerMissingData =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100));
    TestPeer custodyPeerHavingData =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100));
    TestPeer nonCustodyPeer =
        new TestPeer(stubAsyncRunner, nonCustodyNodeIds.next(), Duration.ofMillis(100));

    List<Blob> blobs = Stream.generate(dataStructureUtil::randomValidBlob).limit(1).toList();
    BeaconBlock block = blockResolver.addBlock(10, 1);
    List<DataColumnSidecar> sidecars =
        miscHelpers.constructDataColumnSidecars(createSigned(block), blobs, kzg);
    DataColumnSidecar sidecar0 = sidecars.get(columnIndex.intValue());

    DataColumnSlotAndIdentifier id0 = createId(block, columnIndex.intValue());

    testPeerManager.connectPeer(custodyPeerMissingData);
    testPeerManager.connectPeer(nonCustodyPeer);

    SafeFuture<DataColumnSidecar> resp0 = simpleSidecarRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound.multipliedBy(2));

    assertThat(resp0).isNotDone();
    assertThat(custodyPeerMissingData.getRequests()).hasSize(2);

    custodyPeerHavingData.addSidecar(sidecar0);
    testPeerManager.connectPeer(custodyPeerHavingData);

    advanceTimeGradually(retrieverRound.multipliedBy(2));

    assertThat(resp0).isCompletedWithValue(sidecar0);
    assertThat(nonCustodyPeer.getRequests()).isEmpty();
    assertThat(custodyPeerHavingData.getRequests()).hasSize(1);
    assertThat(custodyPeerMissingData.getRequests()).hasSize(2);
  }

  @Test
  void selectingBestPeerShouldRespectPeerMetrics() {

    TestPeer nonCustodyPeer =
        new TestPeer(stubAsyncRunner, nonCustodyNodeIds.next(), Duration.ofMillis(100));

    TestPeer overloadedCustodyPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(0);

    TestPeer busyCustodyPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(10);

    TestPeer freeCustodyPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    List<TestPeer> allPeers =
        List.of(nonCustodyPeer, overloadedCustodyPeer, busyCustodyPeer, freeCustodyPeer);
    Supplier<List<Integer>> allRequestCountsFunc =
        () -> allPeers.stream().map(peer -> peer.getRequests().size()).toList();

    allPeers.forEach(testPeerManager::connectPeer);

    DataColumnSlotAndIdentifier id0 =
        new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, columnIndex);
    SafeFuture<DataColumnSidecar> resp0 = simpleSidecarRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound);

    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 0, 1));

    advanceTimeGradually(retrieverRound);

    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 1, 1));

    advanceTimeGradually(retrieverRound);

    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 1, 2));

    advanceTimeGradually(retrieverRound);

    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 2, 2));

    overloadedCustodyPeer.currentRequestLimit(1);

    advanceTimeGradually(retrieverRound);

    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 1, 2, 2));
  }

  @Test
  void cancellingRequestShouldRemoveItFromPending() {
    TestPeer custodyPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    testPeerManager.connectPeer(custodyPeer);

    DataColumnSlotAndIdentifier id0 =
        new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, columnIndex);
    SafeFuture<DataColumnSidecar> resp0_0 = simpleSidecarRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound);
    assertThat(custodyPeer.getRequests()).hasSize(1);
    advanceTimeGradually(retrieverRound);
    assertThat(custodyPeer.getRequests()).hasSize(2);

    resp0_0.cancel(true);

    advanceTimeGradually(retrieverRound);
    // after original request is cancelled the retriever should stop requesting peer
    assertThat(custodyPeer.getRequests()).hasSize(2);
    advanceTimeGradually(retrieverRound);
    assertThat(custodyPeer.getRequests()).hasSize(2);
  }

  @Test
  void performanceTest() {
    List<TestPeer> testNodes =
        craftNodeIds()
            .map(
                nodeId ->
                    new TestPeer(stubAsyncRunner, nodeId, Duration.ofMillis(100))
                        .currentRequestLimit(1000))
            .limit(128)
            .peek(node -> custodyCountSupplier.setCustomCount(node.getNodeId(), columnCount))
            .peek(testPeerManager::connectPeer)
            .toList();

    List<DataColumnSlotAndIdentifier> columnIds =
        IntStream.range(0, Integer.MAX_VALUE)
            .mapToObj(UInt64::valueOf)
            .flatMap(
                slot ->
                    IntStream.range(0, columnCount)
                        .mapToObj(UInt64::valueOf)
                        .map(colIdx -> new DataColumnSlotAndIdentifier(slot, Bytes32.ZERO, colIdx)))
            .limit(100_000)
            .toList();

    List<SafeFuture<DataColumnSidecar>> sidecarFutures =
        columnIds.stream().map(simpleSidecarRetriever::retrieve).toList();

    Assertions.assertTimeout(Duration.ofSeconds(10), () -> advanceTimeGradually(retrieverRound));
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldTrackCustodyCountChangesForPeers() {
    Duration responseLatency = Duration.ofDays(1); // complete responses manually
    TestPeer peer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), responseLatency)
            .currentRequestLimit(1000);
    testPeerManager.connectPeer(peer);

    List<DataColumnSlotAndIdentifier> colIds =
        IntStream.range(0, columnCount)
            .mapToObj(UInt64::valueOf)
            .map(colIdx -> new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, colIdx))
            .toList();

    colIds.forEach(simpleSidecarRetriever::retrieve);

    int peerCustodyCount = custodyCountSupplier.getCustodyGroupCountForPeer(peer.getNodeId());

    advanceTimeGradually(retrieverRound);
    assertThat(peer.getRequests()).hasSize(peerCustodyCount);

    int newPeerCustodyCount = peerCustodyCount + 1;
    custodyCountSupplier.setCustomCount(peer.getNodeId(), newPeerCustodyCount);

    advanceTimeGradually(retrieverRound);
    assertThat(peer.getRequests()).hasSize(newPeerCustodyCount);

    custodyCountSupplier.setCustomCount(peer.getNodeId(), columnCount);

    advanceTimeGradually(retrieverRound);
    assertThat(peer.getRequests()).hasSize(columnCount);
  }
}
