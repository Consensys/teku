/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.forkchoice.PayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceUtilGloasTest {

  private static final UInt64 GLOAS_FORK_EPOCH = UInt64.ONE;

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ForkChoiceUtilGloas forkChoiceUtil;
  private UInt64 gloasSlot;

  @BeforeEach
  void setUp() {
    spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
    dataStructureUtil = new DataStructureUtil(spec);
    gloasSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH);
    forkChoiceUtil = ForkChoiceUtilGloas.required(spec.atSlot(gloasSlot).getForkChoiceUtil());
  }

  @Test
  void getParentPayloadStatus_shouldReturnFull_whenParentBlockHashMatchesMessageBlockHash() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<PayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(PayloadStatus.PAYLOAD_STATUS_FULL);
  }

  @Test
  void getParentPayloadStatus_shouldReturnEmpty_whenParentBlockIsPreGloas() {
    // Create parent block which is pre-Gloas
    final UInt64 preGloasSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH).minusMinZero(1);
    final BeaconBlock parentBlock = dataStructureUtil.randomBeaconBlock(preGloasSlot);

    // Create current block that references any block hash (doesn't matter since the check is not
    // done) but its parent is the pre-Gloas block
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(
            parentBlock.getRoot(), dataStructureUtil.randomBytes32());

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<PayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(PayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void getParentPayloadStatus_shouldReturnEmpty_whenParentBlockHashDoesNotMatch() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<PayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(PayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void getParentPayloadStatus_shouldThrowException_whenParentBlockNotFound() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // Test
    assertThat(forkChoiceUtil.getParentPayloadStatus(store, currentBlock.getMessage()))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("Parent block not found");
  }

  @Test
  void isParentNodeFull_shouldReturnTrue_whenParentHasFullPayload() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<Boolean> result = forkChoiceUtil.isParentNodeFull(store, currentBlock);

    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  void isParentNodeFull_shouldReturnFalse_whenParentHasEmptyPayload() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<Boolean> result = forkChoiceUtil.isParentNodeFull(store, currentBlock);

    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  void isParentNodeFull_shouldThrowException_whenParentBlockNotFound() {
    final BeaconBlock currentBlock = dataStructureUtil.randomBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // Test
    assertThat(forkChoiceUtil.getParentPayloadStatus(store, currentBlock))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("Parent block not found");
  }

  // Helper methods to create blocks with specific properties
  private BeaconBlock createBlockWithBlockHash(final Bytes32 blockHash) {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(gloasSlot);
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(block.getBody());
    final SignedExecutionPayloadBid signedBid = body.getSignedExecutionPayloadBid();
    final ExecutionPayloadBid bid = signedBid.getMessage();

    // Create a new bid with the desired block hash
    final ExecutionPayloadBid newBid =
        bid.getSchema()
            .create(
                bid.getParentBlockHash(),
                bid.getParentBlockRoot(),
                blockHash, // Set the desired block hash
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments());

    // Create a new signed bid with the new bid
    final SignedExecutionPayloadBid newSignedBid =
        signedBid.getSchema().create(newBid, signedBid.getSignature());

    // Create a new body with the new signed bid
    final BeaconBlockBody newBody =
        body.getSchema()
            .createBlockBody(
                builder -> {
                  builder
                      .randaoReveal(body.getRandaoReveal())
                      .eth1Data(body.getEth1Data())
                      .graffiti(body.getGraffiti())
                      .attestations(body.getAttestations())
                      .proposerSlashings(body.getProposerSlashings())
                      .attesterSlashings(body.getAttesterSlashings())
                      .deposits(body.getDeposits())
                      .voluntaryExits(body.getVoluntaryExits())
                      .syncAggregate(body.getSyncAggregate())
                      .blsToExecutionChanges(body.getBlsToExecutionChanges())
                      .signedExecutionPayloadBid(newSignedBid)
                      .payloadAttestations(body.getPayloadAttestations());
                  return SafeFuture.COMPLETE;
                })
            .join();

    // Create a new block with the new body
    return block
        .getSchema()
        .create(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            newBody);
  }

  private BeaconBlock createBlockWithParentAndParentBlockHash(
      final Bytes32 parentRoot, final Bytes32 parentBlockHash) {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(gloasSlot);
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(block.getBody());
    final SignedExecutionPayloadBid signedBid = body.getSignedExecutionPayloadBid();
    final ExecutionPayloadBid bid = signedBid.getMessage();

    // Create a new bid with the desired parent block hash
    final ExecutionPayloadBid newBid =
        bid.getSchema()
            .create(
                parentBlockHash, // Set the desired parent block hash
                bid.getParentBlockRoot(),
                bid.getBlockHash(),
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments());

    // Create a new signed bid with the new bid
    final SignedExecutionPayloadBid newSignedBid =
        signedBid.getSchema().create(newBid, signedBid.getSignature());

    // Create a new body with the new signed bid
    final BeaconBlockBody newBody =
        body.getSchema()
            .createBlockBody(
                builder -> {
                  builder
                      .randaoReveal(body.getRandaoReveal())
                      .eth1Data(body.getEth1Data())
                      .graffiti(body.getGraffiti())
                      .attestations(body.getAttestations())
                      .proposerSlashings(body.getProposerSlashings())
                      .attesterSlashings(body.getAttesterSlashings())
                      .deposits(body.getDeposits())
                      .voluntaryExits(body.getVoluntaryExits())
                      .syncAggregate(body.getSyncAggregate())
                      .blsToExecutionChanges(body.getBlsToExecutionChanges())
                      .signedExecutionPayloadBid(newSignedBid)
                      .payloadAttestations(body.getPayloadAttestations());
                  return SafeFuture.COMPLETE;
                })
            .join();

    // Create a new block with the new body and parent root
    return block
        .getSchema()
        .create(
            block.getSlot(),
            block.getProposerIndex(),
            parentRoot, // Set the desired parent root
            block.getStateRoot(),
            newBody);
  }
}
