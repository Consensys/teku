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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockFactoryBellatrixTest extends AbstractBlockFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();

  @Test
  void shouldObtainExecutionPayloadContext() {

    assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), false);

    verify(forkChoiceNotifier).getPayloadId(any(), eq(UInt64.ONE));
  }

  @Test
  void shouldCreateCapellaBlock() {
    final Spec spec = TestSpecFactory.createMinimalCapella();
    final BeaconBlock block =
        assertBlockCreated(1, spec, true, state -> prepareValidPayload(spec, state), false)
            .getBlock();
    final SszList<SignedBlsToExecutionChange> blsToExecutionChanges =
        BeaconBlockBodyCapella.required(block.getBody()).getBlsToExecutionChanges();
    assertThat(blsToExecutionChanges).isNotNull();
  }

  @Test
  void shouldIncludeExecutionPayloadWhenBellatrixIsActive() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final BeaconBlock block =
        assertBlockCreated(1, spec, false, state -> prepareDefaultPayload(spec), false).getBlock();
    final ExecutionPayload result = getExecutionPayload(block);
    assertThat(result).isEqualTo(executionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderWhenBellatrixIsActiveAndBlindedBlockRequested() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final BeaconBlock block =
        assertBlockCreated(1, spec, false, state -> prepareDefaultPayload(spec), true).getBlock();
    final ExecutionPayloadHeader result = getExecutionPayloadHeader(block);
    assertThat(result).isEqualTo(executionPayloadHeader);
  }

  @Test
  void shouldThrowPostMergeWithWrongPayload() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    assertThatThrownBy(
            () -> assertBlockCreated(1, spec, true, state -> prepareDefaultPayload(spec), false))
        .hasCauseInstanceOf(StateTransitionException.class);
  }

  @Test
  void unblindSignedBlock_shouldThrowWhenUnblindingBlockWithInconsistentExecutionPayload() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock signedBlock = dataStructureUtil.randomSignedBlindedBeaconBlock(1);
    executionPayload = dataStructureUtil.randomExecutionPayload();

    assertThatThrownBy(() -> assertBlockUnblinded(signedBlock, spec))
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughUnblindedBlocks() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    final SignedBlockContainer unblindedSignedBlockContainer =
        assertBlockUnblinded(originalUnblindedSignedBlock, spec);

    assertThat(unblindedSignedBlockContainer).isEqualTo(originalUnblindedSignedBlock);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughInNonBellatrixBlocks() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalAltairSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    final SignedBlockContainer unblindedSignedBlockContainer =
        assertBlockUnblinded(originalAltairSignedBlock, spec);

    assertThat(unblindedSignedBlockContainer).isEqualTo(originalAltairSignedBlock);
  }

  @Test
  void unblindSignedBlock_shouldUnblindBlockWhenBellatrixIsActive() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    // now we have a blinded block
    final SignedBeaconBlock originalBlindedSignedBlock =
        assertBlockBlinded(originalUnblindedSignedBlock, spec);

    // let the unblinder return a consistent execution payload
    executionPayload =
        originalUnblindedSignedBlock
            .getMessage()
            .getBody()
            .getOptionalExecutionPayload()
            .orElseThrow();

    assertBlockUnblinded(originalBlindedSignedBlock, spec);
  }

  @Test
  void shouldCreateEmptyBlobSidecarsForBlock() {
    final BlockAndBlobSidecars blockAndBlobSidecars =
        createBlockAndBlobSidecars(false, TestSpecFactory.createMinimalPhase0());

    assertThat(blockAndBlobSidecars.blobSidecars()).isEmpty();
  }

  @Test
  void shouldCreateEmptyBlobSidecarsForBlindedBlock() {
    final BlockAndBlobSidecars blockAndBlobSidecars =
        createBlockAndBlobSidecars(true, TestSpecFactory.createMinimalPhase0());

    assertThat(blockAndBlobSidecars.blobSidecars()).isEmpty();
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final Bytes32 graffiti = dataStructureUtil.randomBytes32();
    return new BlockFactoryBellatrix(
        spec,
        forkChoiceNotifier,
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
            graffiti,
            executionLayer));
  }
}
