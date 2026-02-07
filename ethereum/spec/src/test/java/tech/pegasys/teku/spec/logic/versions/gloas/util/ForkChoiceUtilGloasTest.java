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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.forkchoice.PayloadStatusGloas;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceUtilGloasTest {
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ForkChoiceUtilGloas forkChoiceUtil;

  @BeforeEach
  void setUp() {
    spec = TestSpecFactory.createMinimalGloas();
    dataStructureUtil = new DataStructureUtil(spec);
    forkChoiceUtil = (ForkChoiceUtilGloas) spec.getGenesisSpec().getForkChoiceUtil();
  }

  @Test
  void getParentPayloadStatus_shouldReturnFull_whenParentBlockHashMatchesMessageBlockHash() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final SignedBeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getBlockIfAvailable(currentBlock.getParentRoot()))
        .thenReturn(Optional.of(parentBlock));

    // Test
    final PayloadStatusGloas result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock.getMessage());

    assertThat(result).isEqualTo(PayloadStatusGloas.PAYLOAD_STATUS_FULL);
  }

  @Test
  void getParentPayloadStatus_shouldReturnEmpty_whenParentBlockHashDoesNotMatch() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getBlockIfAvailable(currentBlock.getParentRoot()))
        .thenReturn(Optional.of(parentBlock));

    // Test
    final PayloadStatusGloas result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock.getMessage());

    assertThat(result).isEqualTo(PayloadStatusGloas.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void getParentPayloadStatus_shouldThrowException_whenParentBlockNotFound() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getBlockIfAvailable(currentBlock.getParentRoot())).thenReturn(Optional.empty());

    // Test
    assertThatThrownBy(
            () -> forkChoiceUtil.getParentPayloadStatus(store, currentBlock.getMessage()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Parent block not found");
  }

  @Test
  void isParentNodeFull_shouldReturnTrue_whenParentHasFullPayload() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final SignedBeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getBlockIfAvailable(currentBlock.getParentRoot()))
        .thenReturn(Optional.of(parentBlock));

    // Test
    final boolean result = forkChoiceUtil.isParentNodeFull(store, currentBlock.getMessage());

    assertThat(result).isTrue();
  }

  @Test
  void isParentNodeFull_shouldReturnFalse_whenParentHasEmptyPayload() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getBlockIfAvailable(currentBlock.getParentRoot()))
        .thenReturn(Optional.of(parentBlock));

    // Test
    final boolean result = forkChoiceUtil.isParentNodeFull(store, currentBlock.getMessage());

    assertThat(result).isFalse();
  }

  @Test
  void isParentNodeFull_shouldThrowException_whenParentBlockNotFound() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getBlockIfAvailable(currentBlock.getParentRoot())).thenReturn(Optional.empty());

    // Test
    assertThatThrownBy(() -> forkChoiceUtil.isParentNodeFull(store, currentBlock.getMessage()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Parent block not found");
  }

  // Helper methods to create blocks with specific properties

  private SignedBeaconBlock createBlockWithBlockHash(final Bytes32 blockHash) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final BeaconBlock message = block.getMessage();
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(message.getBody());
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
    final BeaconBlock newMessage =
        message
            .getSchema()
            .create(
                message.getSlot(),
                message.getProposerIndex(),
                message.getParentRoot(),
                message.getStateRoot(),
                newBody);

    // Create a new signed block
    return block.getSchema().create(newMessage, block.getSignature());
  }

  private SignedBeaconBlock createBlockWithParentAndParentBlockHash(
      final Bytes32 parentRoot, final Bytes32 parentBlockHash) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final BeaconBlock message = block.getMessage();
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(message.getBody());
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
    final BeaconBlock newMessage =
        message
            .getSchema()
            .create(
                message.getSlot(),
                message.getProposerIndex(),
                parentRoot, // Set the desired parent root
                message.getStateRoot(),
                newBody);

    // Create a new signed block
    return block.getSchema().create(newMessage, block.getSignature());
  }
}
