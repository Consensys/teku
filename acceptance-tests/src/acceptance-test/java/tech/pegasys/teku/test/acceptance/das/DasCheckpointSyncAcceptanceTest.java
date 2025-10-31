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

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
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
                // we don't want to make this test extreme, withhold once and don't repeat
                .withInteropValidators(0, 64)
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();
    primaryNode.waitForNewFinalization();
    final SignedBeaconBlock finalizedBlock = primaryNode.getFinalizedBlock();

    // late joining full node without validators
    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withCheckpointSyncUrl(primaryNode.getBeaconRestApiUrl())
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                .withInteropValidators(0, 0)
                .build());

    secondaryNode.start();
    secondaryNode.waitUntilInSyncWith(primaryNode);

    final SignedBeaconBlock blockAtHead = secondaryNode.getHeadBlock();

    final int firstFuluSlot =
        primaryNode
            .getSpec()
            .computeStartSlotAtEpoch(
                primaryNode
                    .getSpec()
                    .forMilestone(SpecMilestone.FULU)
                    .getConfig()
                    .getFuluForkEpoch())
            .intValue();
    final int checkpointSlot = finalizedBlock.getSlot().intValue();
    final int endSlot = blockAtHead.getSlot().intValue();

    // after checkpoint is synced with sidecars
    final int afterCheckpointSidecars =
        IntStream.range(checkpointSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(secondaryNode, slot))
            .mapToInt(i -> i)
            .sum();
    assertThat(afterCheckpointSidecars).isGreaterThan(0);

    // before checkpoint sidecars are backfilled
    final int beforeCheckpointSidecars =
        IntStream.range(firstFuluSlot, checkpointSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(secondaryNode, slot))
            .mapToInt(i -> i)
            .sum();
    assertThat(beforeCheckpointSidecars).isGreaterThan(0);
  }

  private int getAndAssertDasCustody(final TekuBeaconNode node, final UInt64 fuluSlot) {
    try {
      Optional<SignedBeaconBlock> maybeBlock = node.getBlockAtSlot(fuluSlot);
      if (maybeBlock.isPresent()) {
        SignedBeaconBlock block = maybeBlock.get();
        boolean hasBlobs =
            !block
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .toVersionDeneb()
                .orElseThrow()
                .getBlobKzgCommitments()
                .isEmpty();
        int columnCount = node.getDataColumnSidecarCount(block.getRoot().toHexString());
        if (hasBlobs) {
          assertThat(columnCount).isNotZero();
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
        .withStubExecutionEngine()
        .withLogLevel("DEBUG");
  }
}
