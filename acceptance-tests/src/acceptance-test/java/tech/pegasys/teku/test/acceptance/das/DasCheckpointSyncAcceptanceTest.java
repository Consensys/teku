/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.test.acceptance.das;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class DasCheckpointSyncAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldBeAbleToCheckpointSyncAndBackfillCustody() throws Exception {
    // supernode with validators
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withSubscribeAllCustodySubnetsEnabled()
                .withInteropValidators(0, 64)
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();

    primaryNode.waitForNewFinalization();
    final SignedBeaconBlock checkpointFinalizedBlock = primaryNode.getFinalizedBlock();
    // We expect at least 1 full epoch in Fulu before checkpoint, so it's greater without equal
    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(primaryNode.getSpec().forMilestone(SpecMilestone.FULU).getConfig());
    assertThat(
            primaryNode
                .getSpec()
                .computeEpochAtSlot(checkpointFinalizedBlock.getSlot())
                .isGreaterThan(specConfigFulu.getFuluForkEpoch()))
        .isTrue();


    // late joining full node without validators with --rest-api-getblobs-sidecars-download-enabled
    final TekuBeaconNode secondaryNode =
            createTekuBeaconNode(
                    createConfigBuilder()
                            .withRealNetwork()
                            .withCheckpointSyncUrl(primaryNode.getBeaconRestApiUrl())
                            .withGenesisTime(genesisTime.intValue())
                            .withGetBlobsSidecarsDownloadApiEnabled()
                            .withPeers(primaryNode)
                            .withInteropValidators(0, 0)
                            .build());

    secondaryNode.start();

    // late joining full node without validators without --rest-api-getblobs-sidecars-download-enabled
    //this mean that when we try to blob sidecars it will 404

    final TekuBeaconNode thirdNode =
            createTekuBeaconNode(
                    createConfigBuilder()
                            .withRealNetwork()
                            .withCheckpointSyncUrl(primaryNode.getBeaconRestApiUrl())
                            .withGenesisTime(genesisTime.intValue())
                            .withPeers(primaryNode)
                            .withInteropValidators(0, 0)
                            .build());

    thirdNode.start();

    secondaryNode.waitUntilInSyncWith(primaryNode);
    thirdNode.waitUntilInSyncWith(primaryNode);


    final UInt64 firstFuluSlot =
        primaryNode.getSpec().computeStartSlotAtEpoch(specConfigFulu.getFuluForkEpoch());

    // Wait until custody is backfilled is completed.
    // It will require:
    // - forward sync to catch up
    // - backfill to complete
    // - custody fully backfilled up to first fulu slot
    final int expectedCustodyCount = specConfigFulu.getCustodyRequirement();
    secondaryNode.waitForCustodyBackfill(firstFuluSlot, expectedCustodyCount);

    final SignedBeaconBlock blockAtHead = secondaryNode.getHeadBlock();
    final int checkpointSlot = checkpointFinalizedBlock.getSlot().intValue();
    final int endSlot = blockAtHead.getSlot().intValue();

    // after checkpoint is synced with sidecars
    final int afterCheckpointSidecars =
        IntStream.range(checkpointSlot + 1, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(secondaryNode, slot, expectedCustodyCount))
            .mapToInt(i -> i)
            .sum();
    assertThat(afterCheckpointSidecars).isGreaterThan(0);

    // checkpoint + before checkpoint sidecars are backfilled
    final int beforeCheckpointSidecars =
        IntStream.rangeClosed(firstFuluSlot.intValue(), checkpointSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(secondaryNode, slot, expectedCustodyCount))
            .mapToInt(i -> i)
            .sum();
    assertThat(beforeCheckpointSidecars).isGreaterThan(0);

    //get a slot to get blobs from
    final UInt64 headSlot = secondaryNode.getHeadBlock().getSlot();

    final Optional<List<Blob>> blobsForBlockNode1 = primaryNode.getBlobsAtSlot(headSlot);
    final int columnsFromSecondNode = secondaryNode.getDataColumnSidecarCount("head");
    assertThat(columnsFromSecondNode).isGreaterThan(0);
    final Optional<List<Blob>> blobsForBlockNode2 = secondaryNode.getBlobsAtSlot(headSlot);

    //assert that blobs obtained from node 1 and node 2 are the same
    assertThat(blobsForBlockNode1).isPresent();
    assertThat(blobsForBlockNode1).isEqualTo(blobsForBlockNode2);

    final int columnsFromThirdNode = thirdNode.getDataColumnSidecarCount("head");
    assertThat(columnsFromThirdNode).isGreaterThan(0);

    //404 are casted into optional empty
    assertThat(thirdNode.getBlobsAtSlot(headSlot)).isEmpty();

  }

  private int getAndAssertDasCustody(
      final TekuBeaconNode node, final UInt64 fuluSlot, final int expectedCustodyCount) {
    try {
      final Optional<SignedBeaconBlock> maybeBlock = node.getBlockAtSlot(fuluSlot);
      if (maybeBlock.isPresent()) {
        final SignedBeaconBlock block = maybeBlock.get();
        final boolean hasBlobs =
            !block
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .toVersionDeneb()
                .orElseThrow()
                .getBlobKzgCommitments()
                .isEmpty();
        final int columnCount = node.getDataColumnSidecarCount(block.getRoot().toHexString());
        if (hasBlobs) {
          assertThat(columnCount).isEqualTo(expectedCustodyCount);
        } else {
          assertThat(columnCount).isZero();
        }
        return columnCount;
      } else {
        return 0;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TekuNodeConfigBuilder createConfigBuilder() throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withNetwork(Resources.getResource("fulu-minimal.yaml"))
        .withStubExecutionEngine(3)
        .withReworkedCustodySync()
        .withLogLevel("DEBUG");
  }
}
