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
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class Das50PercentRecoveryAcceptanceTest extends AcceptanceTestBase {

  private final int subnetCount = 128;

  @Test
  public void shouldAbleToReconstructDataColumnSidecarsFrom50Percent_whenOnGossip()
      throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder()
                .withRealNetwork()
                // interop validators are not count for validator custody
                .withCustodyGroupCountOverride(subnetCount / 2)
                // we don't want to make this test extreme, withhold once and don't repeat
                .withDasPublishWithholdColumnsEverySlots(9999)
                .withInteropValidators(0, 64)
                .withDasDisableElRecovery()
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();

    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder()
                .withRealNetwork()
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                // supernode
                .withSubscribeAllCustodySubnetsEnabled()
                .withInteropValidators(0, 0)
                .withDasDisableElRecovery()
                .build());

    secondaryNode.start();
    // DataColumnSidecars are withheld on primaryNode
    primaryNode.waitForLogMessageContaining("non-custodied sidecars at");
    // DataColumnSidecars are reconstructed on secondaryNode
    secondaryNode.waitForLogMessageContaining("Data column sidecars recovery finished for block");
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
    secondaryNode.waitForBlockSatisfying(
        block -> assertThat(block.getSlot().intValue()).isGreaterThanOrEqualTo(firstFuluSlot));

    final SignedBeaconBlock blockAtHead = secondaryNode.getHeadBlock();

    final int endSlot = blockAtHead.getSlot().intValue();

    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(primaryNode.getSpec().forMilestone(SpecMilestone.FULU).getConfig());
    final int allFuluColumns =
        IntStream.rangeClosed(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(
                slot ->
                    getAndAssertDasCustody(
                        secondaryNode, slot, specConfigFulu.getNumberOfColumns()))
            .mapToInt(i -> i)
            .sum();

    assertThat(allFuluColumns).isGreaterThan(0);
  }

  @Test
  public void
      shouldAbleToReconstructDataColumnSidecarsFrom50Percent_whenSyncingWithReworkedRetriever()
          throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder()
                .withRealNetwork()
                .withDiscoveryNetwork()
                // interop validators are not count for validator custody
                .withCustodyGroupCountOverride(subnetCount / 2)
                // we don't want to make this test extreme, withhold once and don't repeat
                .withDasPublishWithholdColumnsEverySlots(9999)
                .withInteropValidators(0, 64)
                .withDasDisableElRecovery()
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();

    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder()
                .withRealNetwork()
                .withDiscoveryNetwork()
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                // supernode
                .withSubscribeAllCustodySubnetsEnabled()
                .withInteropValidators(0, 0)
                .withDasDisableElRecovery()
                .withReworkedRecovery()
                .withReworkedRecoveryTimeouts(100_000, 40_000)
                .build());

    // Wait for few epochs, so sync will kick-in when second node is started
    primaryNode.waitForEpochAtOrAbove(2);
    primaryNode.waitForEpochAtOrAbove(3);

    secondaryNode.start();
    // DataColumnSidecars are reconstructed on secondaryNode
    secondaryNode.waitForLogMessageContaining("Rebuilding columns for slot");
    secondaryNode.waitForLogMessageContaining("Rebuilding columns at");
    secondaryNode.waitForLogMessageContaining("Rebuilding columns DONE");
    // as first node custodies only 64 columns, it means we need to rebuild every single slot
    // sidecars one by one, so we will not wait for finalization, one slot recovery is ok

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
    secondaryNode.waitForBlockSatisfying(
        block -> assertThat(block.getSlot().intValue()).isGreaterThanOrEqualTo(firstFuluSlot),
        2,
        TimeUnit.MINUTES);
    final SignedBeaconBlock blockAtHead = secondaryNode.getHeadBlock();

    final int endSlot = blockAtHead.getSlot().intValue();
    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(primaryNode.getSpec().forMilestone(SpecMilestone.FULU).getConfig());
    final int allFuluColumns =
        IntStream.rangeClosed(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(
                slot ->
                    getAndAssertDasCustody(
                        secondaryNode, slot, specConfigFulu.getNumberOfColumns()))
            .mapToInt(i -> i)
            .sum();

    assertThat(allFuluColumns).isGreaterThan(0);
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

  private TekuNodeConfigBuilder createFuluMinimalConfigBuilder() throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withNetwork(Resources.getResource("fulu-minimal.yaml"))
        .withStubExecutionEngine()
        .withLogLevel("DEBUG");
  }
}
