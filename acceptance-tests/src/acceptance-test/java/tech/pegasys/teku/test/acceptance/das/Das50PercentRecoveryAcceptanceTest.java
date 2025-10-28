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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class Das50PercentRecoveryAcceptanceTest extends AcceptanceTestBase {
  private static final Logger LOG = LogManager.getLogger();

  private final int subnetCount = 128;
  private final AsyncRunnerFactory asyncRunnerFactory =
      AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(new StubMetricsSystem()));

  @Test
  public void shouldAbleToReconstructDataColumnSidecarsFrom50Percents_whenOnGossip()
      throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withDasExtraCustodyGroupCount(subnetCount / 2)
                // we don't want to make this test extreme, withhold once and don't repeat
                .withDasPublishWithholdColumnsEverySlots(9999)
                // validators custody requirement for 64 validators
                .withInteropValidators(0, 64)
                .withDasDisableElRecovery()
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();

    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                // supernode
                .withSubscribeAllCustodySubnetsEnabled()
                .withInteropValidators(0, 0)
                .withDasDisableElRecovery()
                .build());

    secondaryNode.start();
    AsyncRunner test = asyncRunnerFactory.create("test", 2);
    CountDownLatch latch = new CountDownLatch(2);
    test.runAsync(
            () -> {
              // DataColumnSidecars are withheld on primaryNode
              primaryNode.waitForLogMessageContaining("non-custodied sidecars at");
              latch.countDown();
            })
        .finishDebug(LOG);
    test.runAsync(
            () -> {
              // DataColumnSidecars are reconstructed on secondaryNode
              secondaryNode.waitForLogMessageContaining(
                  "Data column sidecars recovery finished for block");
              latch.countDown();
            })
        .finishDebug(LOG);
    assertThat(latch.await(2, TimeUnit.MINUTES)).isTrue();

    secondaryNode.waitForNewFinalization();

    final SignedBeaconBlock blockAtHead = secondaryNode.getBlockAtHead();

    int epochSlots = primaryNode.getSpec().slotsPerEpoch(UInt64.ZERO);
    int endSlot = blockAtHead.getSlot().intValue();
    int firstFuluSlot =
        primaryNode
                .getSpec()
                .forMilestone(SpecMilestone.FULU)
                .getConfig()
                .getFuluForkEpoch()
                .intValue()
            * epochSlots;
    int totalColumns =
        IntStream.range(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(secondaryNode, slot))
            .mapToInt(i -> i)
            .sum();

    assertThat(totalColumns).isGreaterThan(0);
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
