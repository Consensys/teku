/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockFactoryGloasTest extends AbstractBlockFactoryTest {

  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NO_OP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldCreateBlock() {
    assertBlockCreated(1, spec, false, state -> {}, false).blockContainer();
  }

  @Test
  void unblindSignedBlock_shouldPassthroughUnblindedBlock() {
    final SignedBeaconBlock signedBlock = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock unblindedSignedBlock = assertBlockUnblinded(signedBlock, spec);
    assertThat(unblindedSignedBlock).isEqualTo(signedBlock);
  }

  @Test
  void unblindSignedBlock_shouldFailIfBlockIsBlinded() {
    final SignedBeaconBlock signedBlindedBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    final BlockFactory blockFactory = createBlockFactory(spec);
    assertThatThrownBy(
            () ->
                blockFactory.unblindSignedBlockIfBlinded(
                    signedBlindedBlock, BlockPublishingPerformance.NOOP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Blocks in ePBS should be all unblinded");
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    return new BlockFactoryGloas(
        spec,
        new BlockOperationSelectorFactory(
            spec,
            attestationsPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            depositProvider,
            eth1DataCache,
            graffitiBuilder,
            forkChoiceNotifier,
            executionLayer,
            metricsSystem,
            timeProvider));
  }
}
