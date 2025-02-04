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

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class DasSyncAcceptanceTest extends AcceptanceTestBase {

  private final int subnetCount = 128;
  private final int defaultCustodySubnetCount = 4;
  private final int fuluEpoch = 1;

  @Test
  public void shouldSyncToNodeWithGreaterFinalizedEpoch() throws Exception {
    final int secondNodeStartEpoch = fuluEpoch + 1;
    final int finalCheckEpoch = secondNodeStartEpoch + 2;
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withDasExtraCustodyGroupCount(subnetCount - defaultCustodySubnetCount)
                .build());

    primaryNode.start();
    UInt64 genesisTime = primaryNode.getGenesisTime();
    final TekuBeaconNode lateJoiningNode =
        createLateJoiningNode(primaryNode, genesisTime.intValue());
    primaryNode.waitForEpochAtOrAbove(secondNodeStartEpoch, Duration.ofMinutes(5));

    lateJoiningNode.start();
    lateJoiningNode.waitForGenesis();
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
    lateJoiningNode.waitForEpochAtOrAbove(finalCheckEpoch, Duration.ofMinutes(5));
    lateJoiningNode.waitUntilInSyncWith(primaryNode);

    int epochSlots = primaryNode.getSpec().slotsPerEpoch(UInt64.ZERO);
    int endSlot = (finalCheckEpoch + 1) * epochSlots;
    int firstFuluSlot = fuluEpoch * epochSlots;
    int totalColumns =
        IntStream.range(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(lateJoiningNode, slot))
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

  private TekuBeaconNode createLateJoiningNode(
      final TekuBeaconNode primaryNode, final int genesisTime) throws IOException {
    return createTekuBeaconNode(
        createConfigBuilder()
            .withGenesisTime(genesisTime)
            .withRealNetwork()
            .withPeers(primaryNode)
            .withInteropValidators(0, 0)
            .build());
  }

  private TekuNodeConfigBuilder createConfigBuilder() throws IOException {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withNetwork("minimal")
        .withAltairEpoch(UInt64.valueOf(0))
        .withBellatrixEpoch(UInt64.valueOf(0))
        .withCapellaEpoch(UInt64.valueOf(0))
        .withDenebEpoch(UInt64.valueOf(0))
        .withElectraEpoch(UInt64.valueOf(0))
        .withFuluEpoch(UInt64.valueOf(fuluEpoch))
        .withStubExecutionEngine()
        .withStubBlobCount(Optional.of(1))
        .withLogLevel("DEBUG");
  }
}
