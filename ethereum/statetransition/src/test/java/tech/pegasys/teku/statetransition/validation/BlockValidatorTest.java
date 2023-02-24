/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX, SpecMilestone.DENEB})
public class BlockValidatorTest {
  private Spec spec;
  private RecentChainData recentChainData;
  private StorageSystem storageSystem;

  private BlockValidator blockValidator;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();
    blockValidator = new BlockValidator(spec, recentChainData);
  }

  @TestTemplate
  void shouldReturnValidForValidBlock() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    InternalValidationResult result = blockValidator.validate(block).join();
    assertTrue(result.isAccept());
  }

  @TestTemplate
  void shouldIgnoreAlreadyImportedBlock() {
    final SignedBeaconBlock block = storageSystem.chainUpdater().advanceChain().getBlock();

    InternalValidationResult result = blockValidator.validate(block).join();
    assertTrue(result.isIgnore());
  }

  @TestTemplate
  void shouldReturnInvalidForSecondValidBlockForSlotAndProposer() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    InternalValidationResult result1 = blockValidator.validate(block).join();
    assertTrue(result1.isAccept());

    InternalValidationResult result2 = blockValidator.validate(block).join();
    assertTrue(result2.isIgnore());
  }

  @TestTemplate
  void shouldReturnSavedForFutureForBlockFromFuture() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBeaconBlock block =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    InternalValidationResult result = blockValidator.validate(block).join();
    assertTrue(result.isSaveForFuture());
  }

  @TestTemplate
  void shouldReturnSavedForFutureForBlockWithParentUnavailable() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    final UInt64 proposerIndex = signedBlock.getMessage().getProposerIndex();
    final BeaconBlock block =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            signedBlock.getSlot(),
            proposerIndex,
            Bytes32.ZERO,
            signedBlock.getMessage().getStateRoot(),
            signedBlock.getMessage().getBody());

    BLSSignature blockSignature =
        storageSystem
            .chainBuilder()
            .getSigner(proposerIndex.intValue())
            .signBlock(
                block,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();
    final SignedBeaconBlock blockWithNoParent =
        SignedBeaconBlock.create(spec, block, blockSignature);

    InternalValidationResult result = blockValidator.validate(blockWithNoParent).join();
    assertTrue(result.isSaveForFuture());
  }

  @TestTemplate
  void shouldReturnInvalidForBlockOlderThanFinalizedSlot() {
    UInt64 finalizedEpoch = UInt64.valueOf(10);
    UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    storageSystem.chainUpdater().advanceChain(finalizedSlot);
    storageSystem.chainUpdater().finalizeEpoch(finalizedEpoch);

    StorageSystem storageSystem2 = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem2.chainUpdater().initializeGenesis(false);
    final SignedBeaconBlock block =
        storageSystem2.chainBuilder().generateBlockAtSlot(finalizedSlot.minus(ONE)).getBlock();

    InternalValidationResult result = blockValidator.validate(block).join();
    assertTrue(result.isIgnore());
  }

  @TestTemplate
  void shouldReturnInvalidForBlockWithWrongProposerIndex() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    UInt64 invalidProposerIndex = signedBlock.getMessage().getProposerIndex().plus(ONE);

    final BeaconBlock block =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            signedBlock.getSlot(),
            invalidProposerIndex,
            signedBlock.getParentRoot(),
            signedBlock.getMessage().getStateRoot(),
            signedBlock.getMessage().getBody());

    BLSSignature blockSignature =
        storageSystem
            .chainBuilder()
            .getSigner(invalidProposerIndex.intValue())
            .signBlock(
                block,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();
    final SignedBeaconBlock invalidProposerSignedBlock =
        SignedBeaconBlock.create(spec, block, blockSignature);

    InternalValidationResult result = blockValidator.validate(invalidProposerSignedBlock).join();
    assertTrue(result.isReject());
  }

  @TestTemplate
  void shouldReturnInvalidForBlockWithWrongSignature() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock block =
        SignedBeaconBlock.create(
            spec,
            storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock().getMessage(),
            BLSTestUtil.randomSignature(0));

    InternalValidationResult result = blockValidator.validate(block).join();
    assertTrue(result.isReject());
  }

  @TestTemplate
  void shouldReturnInvalidForBlockThatDoesNotDescendFromFinalizedCheckpoint() {
    List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(4);

    StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    ChainBuilder chainBuilder = ChainBuilder.create(spec, validatorKeys);
    ChainUpdater chainUpdater =
        new ChainUpdater(storageSystem.recentChainData(), chainBuilder, spec);

    BlockValidator blockValidator = new BlockValidator(spec, storageSystem.recentChainData());
    chainUpdater.initializeGenesis();

    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder chainBuilderFork = chainBuilder.fork();
    ChainUpdater chainUpdaterFork =
        new ChainUpdater(storageSystem.recentChainData(), chainBuilderFork, spec);

    UInt64 startSlotOfFinalizedEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(4));

    chainUpdaterFork.advanceChain(20);

    chainUpdater.finalizeEpoch(4);

    SignedBlockAndState blockAndState =
        chainBuilderFork.generateBlockAtSlot(startSlotOfFinalizedEpoch.increment());
    chainUpdater.saveBlockTime(blockAndState);
    final SafeFuture<InternalValidationResult> result =
        blockValidator.validate(blockAndState.getBlock());
    assertThat(result).isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldReturnAcceptOnCorrectExecutionPayloadTimestamp(final SpecContext specContext) {
    specContext.assumeBellatrixActive();

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    blockValidator = new BlockValidator(spec, recentChainData);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    SignedBeaconBlock block = storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    InternalValidationResult result = blockValidator.validate(block).join();
    assertTrue(result.isAccept());
  }

  @TestTemplate
  void shouldReturnInvalidOnWrongExecutionPayloadTimestamp(final SpecContext specContext) {
    specContext.assumeBellatrixActive();

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    blockValidator = new BlockValidator(spec, recentChainData);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBlockAndState signedBlockAndState =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(
                nextSlot,
                BlockOptions.create()
                    .setSkipStateTransition(true)
                    .setExecutionPayload(
                        specContext.getDataStructureUtil().randomExecutionPayload()));

    InternalValidationResult result =
        blockValidator.validate(signedBlockAndState.getBlock()).join();
    assertTrue(result.isReject());
  }
}
