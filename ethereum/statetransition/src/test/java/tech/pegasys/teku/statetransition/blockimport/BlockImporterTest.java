/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.blockimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.core.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;

public class BlockImporterTest {
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(8);
  private final EventBus localEventBus = mock(EventBus.class);
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(localEventBus);
  private final BeaconChainUtil localChain =
      BeaconChainUtil.create(recentChainData, validatorKeys, false);

  private final EventBus otherEventBus = mock(EventBus.class);
  private final RecentChainData otherStorage = MemoryOnlyRecentChainData.create(otherEventBus);
  private final BeaconChainUtil otherChain =
      BeaconChainUtil.create(otherStorage, validatorKeys, false);

  private final BlockImporter blockImporter = new BlockImporter(recentChainData, localEventBus);

  @BeforeAll
  public static void init() {
    Constants.SLOTS_PER_EPOCH = 6;
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void dispose() {
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = true;
  }

  @BeforeEach
  public void setup() {
    otherChain.initializeStorage();
    localChain.initializeStorage();
  }

  @Test
  public void importBlock_success() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UnsignedLong.ONE);
    localChain.setSlot(block.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block);
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_alreadyInChain() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UnsignedLong.ONE);
    localChain.setSlot(block.getSlot());

    assertThat(blockImporter.importBlock(block).isSuccessful()).isTrue();
    BlockImportResult result = blockImporter.importBlock(block);
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_validAttestations() throws Exception {

    UnsignedLong currentSlot = UnsignedLong.ONE;
    SignedBeaconBlock block1 = localChain.createAndImportBlockAtSlot(currentSlot);
    currentSlot = currentSlot.plus(UnsignedLong.ONE);

    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconState state = recentChainData.getBlockState(block1.getRoot()).orElseThrow();
    List<Attestation> attestations =
        attestationGenerator.getAttestationsForSlot(state, block1.getMessage(), currentSlot);
    List<Attestation> aggregatedAttestations =
        AttestationGenerator.groupAndAggregateAttestations(attestations);

    currentSlot = currentSlot.plus(UnsignedLong.ONE);

    localChain.createAndImportBlockAtSlotWithAttestations(currentSlot, aggregatedAttestations);
  }

  @Test
  public void importBlock_attestationWithInvalidSignature() throws Exception {

    UnsignedLong currentSlot = UnsignedLong.ONE;
    SignedBeaconBlock block1 = localChain.createAndImportBlockAtSlot(currentSlot);
    currentSlot = currentSlot.plus(UnsignedLong.ONE);

    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconState state = recentChainData.getBlockState(block1.getRoot()).orElseThrow();
    List<Attestation> attestations =
        attestationGenerator.getAttestationsForSlot(state, block1.getMessage(), currentSlot);
    List<Attestation> aggregatedAttestations =
        AttestationGenerator.groupAndAggregateAttestations(attestations);

    // make one attestation signature invalid
    aggregatedAttestations
        .get(aggregatedAttestations.size() / 2)
        .setAggregate_signature(BLSSignature.random(1));

    UnsignedLong currentSlotFinal = currentSlot.plus(UnsignedLong.ONE);

    assertThatCode(
            () -> {
              localChain.createAndImportBlockAtSlotWithAttestations(
                  currentSlotFinal, aggregatedAttestations);
            })
        .hasMessageContaining("signature");
  }

  @Test
  public void importBlock_latestFinalizedBlock() throws Exception {
    Constants.SLOTS_PER_EPOCH = 6;

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    UnsignedLong currentSlot = recentChainData.getBestSlot();
    for (int i = 0; i < Constants.SLOTS_PER_EPOCH; i++) {
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
      final SignedBeaconBlock block = localChain.createAndImportBlockAtSlot(currentSlot);
      blocks.add(block);
    }

    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 bestRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UnsignedLong bestEpoch = compute_epoch_at_slot(recentChainData.getBestSlot());
    assertThat(bestEpoch.longValue()).isEqualTo(Constants.GENESIS_EPOCH + 1L);
    final Checkpoint finalized = new Checkpoint(bestEpoch, bestRoot);
    tx.setFinalizedCheckpoint(finalized);
    tx.commit().join();

    // Known blocks should report as successfully imported
    final BlockImportResult result = blockImporter.importBlock(blocks.get(blocks.size() - 1));
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_knownBlockOlderThanLatestFinalized() throws Exception {
    Constants.SLOTS_PER_EPOCH = 6;

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    UnsignedLong currentSlot = recentChainData.getBestSlot();
    for (int i = 0; i < Constants.SLOTS_PER_EPOCH; i++) {
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
      final SignedBeaconBlock block = localChain.createAndImportBlockAtSlot(currentSlot);
      blocks.add(block);
    }

    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 bestRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UnsignedLong bestEpoch = compute_epoch_at_slot(recentChainData.getBestSlot());
    assertThat(bestEpoch.longValue()).isEqualTo(Constants.GENESIS_EPOCH + 1L);
    final Checkpoint finalized = new Checkpoint(bestEpoch, bestRoot);
    tx.setFinalizedCheckpoint(finalized);
    tx.commit().join();

    // Import a block prior to the latest finalized block
    final BlockImportResult result = blockImporter.importBlock(blocks.get(1));
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_parentBlockFromSameSlot() throws Exception {
    // First import a valid block at slot 1
    final SignedBeaconBlock block = otherChain.createAndImportBlockAtSlot(UnsignedLong.ONE);
    localChain.setSlot(block.getSlot());
    assertSuccessfulResult(blockImporter.importBlock(block));

    // Now create an alternate block 1 with the real block one as the parent block
    final BeaconBlock invalidAncestryUnsignedBlock =
        new BeaconBlock(
            block.getSlot(),
            block.getMessage().getProposer_index(),
            block.getMessage().hash_tree_root(),
            block.getMessage().getState_root(),
            block.getMessage().getBody());
    final Signer signer =
        new Signer(localChain.getSigner(block.getMessage().getProposer_index().intValue()));
    final SignedBeaconBlock invalidAncestryBlock =
        new SignedBeaconBlock(
            invalidAncestryUnsignedBlock,
            signer
                .signBlock(
                    invalidAncestryUnsignedBlock, otherStorage.getHeadForkInfo().orElseThrow())
                .join());

    final BlockImportResult result = blockImporter.importBlock(invalidAncestryBlock);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason())
        .isEqualTo(BlockImportResult.FAILED_INVALID_ANCESTRY.getFailureReason());
  }

  private void assertSuccessfulResult(final BlockImportResult result) {
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFailureReason()).isNull();
    assertThat(result.getFailureCause()).isEmpty();
    assertThat(result.getBlockProcessingRecord()).isNotNull();
  }

  @Test
  public void importBlock_fromFuture() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UnsignedLong.ONE);

    final BlockImportResult result = blockImporter.importBlock(block);
    assertImportFailed(result, FailureReason.BLOCK_IS_FROM_FUTURE);
  }

  @Test
  public void importBlock_unknownParent() throws Exception {
    otherChain.createAndImportBlockAtSlot(UnsignedLong.ONE);
    final SignedBeaconBlock block2 = otherChain.createAndImportBlockAtSlot(UnsignedLong.valueOf(2));
    localChain.setSlot(block2.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block2);
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_wrongChain() throws Exception {
    UnsignedLong currentSlot = recentChainData.getBestSlot();
    for (int i = 0; i < 3; i++) {
      currentSlot = currentSlot.plus(UnsignedLong.ONE);
      localChain.createAndImportBlockAtSlot(currentSlot);
    }
    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 finalizedRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UnsignedLong finalizedEpoch = UnsignedLong.ONE;
    final Checkpoint finalized = new Checkpoint(finalizedEpoch, finalizedRoot);
    tx.setFinalizedCheckpoint(finalized);
    tx.commit().join();

    // Now create a new block that is not descendent from the finalized block
    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = otherStorage.getBestBlockAndState().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final SignedBeaconBlock block =
        otherChain.createAndImportBlockAtSlotWithAttestations(currentSlot, List.of(attestation));

    final BlockImportResult result = blockImporter.importBlock(block);
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_invalidStateTransition() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UnsignedLong.ONE);
    block.getMessage().setState_root(Bytes32.ZERO);
    localChain.setSlot(block.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block);
    assertImportFailed(result, FailureReason.FAILED_STATE_TRANSITION);
  }

  private void assertImportFailed(
      final BlockImportResult result, final BlockImportResult.FailureReason expectedReason) {
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason()).isEqualTo(expectedReason);
  }
}
