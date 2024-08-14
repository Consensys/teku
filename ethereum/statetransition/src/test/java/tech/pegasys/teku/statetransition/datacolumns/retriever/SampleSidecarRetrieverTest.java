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
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Disabled;
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
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolverStub;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarValidator;

@SuppressWarnings("unused")
public class SampleSidecarRetrieverTest {
  final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);
  final DataColumnPeerSearcherStub dataColumnPeerSearcherStub = new DataColumnPeerSearcherStub();
  final TestPeerManager testPeerManager = new TestPeerManager();

  final Spec spec = TestSpecFactory.createMinimalEip7594();
  final SpecConfigEip7594 config =
      SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
  final MiscHelpersEip7594 miscHelpers =
      MiscHelpersEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).miscHelpers());
  final int columnCount = config.getNumberOfColumns();
  final KZG kzg = KZG.getInstance(false);

  final DasPeerCustodyCountSupplier custodyCountSupplier =
      DasPeerCustodyCountSupplier.createStub(config.getCustodyRequirement());

  final SimpleSidecarRetriever simpleSidecarRetriever =
      new SimpleSidecarRetriever(
          spec,
          testPeerManager,
          dataColumnPeerSearcherStub,
          custodyCountSupplier,
          testPeerManager,
          DataColumnSidecarValidator.NOOP,
          stubAsyncRunner,
          Duration.ofSeconds(1));

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);

  public SampleSidecarRetrieverTest() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  private SignedBeaconBlock createSigned(BeaconBlock block) {
    return dataStructureUtil.signedBlock(block);
  }

  private ColumnSlotAndIdentifier createId(BeaconBlock block, int colIdx) {
    return new ColumnSlotAndIdentifier(
        block.getSlot(), new DataColumnIdentifier(block.getRoot(), UInt64.valueOf(colIdx)));
  }

  List<UInt64> nodeCustodyColumns(UInt256 nodeId) {
    return miscHelpers.computeCustodyColumnIndexes(
        nodeId, custodyCountSupplier.getCustodyCountForPeer(nodeId));
  }

  Stream<UInt256> craftNodeIds() {
    return IntStream.iterate(0, i -> i + 1).mapToObj(UInt256::valueOf);
  }

  Stream<UInt256> craftNodeIdsCustodyOf(UInt64 custodyColumn) {
    return craftNodeIds().filter(nodeId -> nodeCustodyColumns(nodeId).contains(custodyColumn));
  }

  Stream<UInt256> craftNodeIdsNotCustodyOf(UInt64 custodyColumn) {
    return craftNodeIds().filter(nodeId -> !nodeCustodyColumns(nodeId).contains(custodyColumn));
  }

  private void advanceTimeGradually(Duration delta) {
    for (int i = 0; i < delta.toMillis(); i++) {
      stubTimeProvider.advanceTimeBy(Duration.ofMillis(1));
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
  }

  @Disabled
  @Test
  void sanityTest() {
    UInt64 columnId = UInt64.valueOf(1);

    List<TestPeer> custodyPeers =
        craftNodeIdsCustodyOf(columnId)
            .limit(2)
            .map(nodeId -> new TestPeer(stubAsyncRunner, nodeId, Duration.ofMillis(100)))
            .peek(testPeer -> testPeer.setCurrentRequestLimit(1000))
            .toList();

    List<TestPeer> nonCustodyPeers =
        craftNodeIdsNotCustodyOf(columnId)
            .limit(1)
            .map(nodeId -> new TestPeer(stubAsyncRunner, nodeId, Duration.ofMillis(100)))
            .peek(testPeer -> testPeer.setCurrentRequestLimit(1000))
            .toList();

    List<Blob> blobs = Stream.generate(dataStructureUtil::randomBlob).limit(1).toList();
    BeaconBlock block = blockResolver.addBlock(10, 1);
    List<DataColumnSidecar> sidecars =
        miscHelpers.constructDataColumnSidecars(createSigned(block), blobs, kzg);
    DataColumnSidecar sidecar0 = sidecars.get(columnId.intValue());

    ColumnSlotAndIdentifier id0 = createId(block, columnId.intValue());

    testPeerManager.connectPeer(custodyPeers.get(0));
    testPeerManager.connectPeer(nonCustodyPeers.get(0));

    SafeFuture<DataColumnSidecar> resp0 = simpleSidecarRetriever.retrieve(id0);

    advanceTimeGradually(Duration.ofSeconds(2));

    assertThat(resp0).isNotDone();
    assertThat(nonCustodyPeers.get(0).getRequests()).isEmpty();
    assertThat(custodyPeers.get(0).getRequests()).isNotEmpty();

    custodyPeers.get(1).addSidecar(sidecar0);
    testPeerManager.connectPeer(custodyPeers.get(1));

    advanceTimeGradually(Duration.ofSeconds(30));

    assertThat(nonCustodyPeers.get(0).getRequests()).isEmpty();
    // TOFIX: The Retriever continues to request from the custodyPeers.get(0) even while it misses
    // the column
    // and the new connected peer custodyPeers.get(1) has it
    assertThat(resp0).isCompletedWithValue(sidecar0);
    assertThat(custodyPeers.get(1).getRequests()).isNotEmpty();
    assertThat(custodyPeers.get(0).getRequests().size()).isLessThan(3);
  }
}
