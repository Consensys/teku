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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.statetransition.util.StartupUtil.get_eth1_data_stub;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.MockMessageApi;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class BlocksTopicHandlerTest {

  private final PubsubPublisherApi publisher = mock(PubsubPublisherApi.class);
  private final EventBus eventBus = spy(new EventBus());
  private final ChainStorageClient storageClient = new ChainStorageClient(eventBus);
  final List<BLSKeyPair> validatorKeys =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 12);

  private final BlocksTopicHandler blocksTopicHandler =
      new BlocksTopicHandler(publisher, eventBus, storageClient);

  ArgumentCaptor<ByteBuf> byteBufCaptor = ArgumentCaptor.forClass(ByteBuf.class);
  ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);

  @BeforeEach
  public void setup() {
    StartupUtil.setupInitialState(storageClient, 0, null, validatorKeys);
    eventBus.register(blocksTopicHandler);
  }

  @Test
  public void onNewBlock() {
    // Should gossip new blocks received from event bus
    BeaconBlock block = DataStructureUtil.randomBeaconBlock(1, 100);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);
    eventBus.post(block);

    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(blocksTopicHandler.getTopic());
  }

  @Test
  public void accept_validBlock() throws Exception {
    final ProposedBlock proposedBlock =
        createBlockAtSlot(storageClient.getBestSlot().longValue() + 1L, true);
    BeaconBlock block = proposedBlock.getBlock();
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher).publish(byteBufCaptor.capture(), topicCaptor.capture());
    assertThat(byteBufCaptor.getValue().array()).isEqualTo(serialized.toArray());
    assertThat(topicCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(topicCaptor.getValue()).isEqualTo(blocksTopicHandler.getTopic());
  }

  @Test
  public void accept_invalidBlock_random() {
    BeaconBlock block = DataStructureUtil.randomBeaconBlock(1, 100);
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }

  @Test
  public void accept_invalidBlock_badData() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }

  @Test
  public void accept_invalidBlock_wrongProposer() throws Exception {
    final ProposedBlock proposedBlock =
        createBlockAtSlot(storageClient.getBestSlot().longValue() + 1L, false);
    BeaconBlock block = proposedBlock.getBlock();
    Bytes serialized = SimpleOffsetSerializer.serialize(block);

    final MessageApi mockMessage = new MockMessageApi(serialized, blocksTopicHandler.getTopic());
    blocksTopicHandler.accept(mockMessage);

    verify(publisher, never()).publish(any(), any());
  }

  private ProposedBlock createBlockAtSlot(final long slot, boolean withValidProposer)
      throws Exception {
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    final BeaconBlock bestBlock = storageClient.getStore().getBlock(bestBlockRoot);
    final BeaconState preState = storageClient.getBestBlockRootState();

    final BeaconStateWithCache postState = BeaconStateWithCache.fromBeaconState(preState);
    final StateTransition stateTransition = new StateTransition(false);
    stateTransition.process_slots(postState, UnsignedLong.valueOf(slot), false);

    final int correctProposerIndex = get_beacon_proposer_index(postState);
    final int proposerIndex =
        withValidProposer
            ? correctProposerIndex
            : (correctProposerIndex + 1) % postState.getValidators().size();
    final Validator proposer = postState.getValidators().get(proposerIndex);

    final SSZList<Deposit> deposits = new SSZList<>(Deposit.class, MAX_DEPOSITS);
    final SSZList<Attestation> attestations = new SSZList<>(Attestation.class, MAX_ATTESTATIONS);

    BeaconBlock newBlock =
        createBeaconBlock(postState, bestBlock.signing_root("signature"), deposits, attestations);
    BLSKeyPair proposerKey = validatorKeys.get(proposerIndex);
    UnsignedLong epoch = get_current_epoch(postState);
    Bytes domain = get_domain(postState, DOMAIN_BEACON_PROPOSER, epoch);
    newBlock.setSignature(
        BLSSignature.sign(proposerKey, newBlock.signing_root("signature"), domain));

    return new ProposedBlock(newBlock, proposer, proposerIndex);
  }

  private BeaconBlock createBeaconBlock(
      BeaconState postState,
      Bytes32 parentBlockRoot,
      SSZList<Deposit> deposits,
      SSZList<Attestation> attestations) {

    final Bytes32 stateRoot = postState.hash_tree_root();
    BeaconBlockBody beaconBlockBody = new BeaconBlockBody();
    UnsignedLong slot = postState.getSlot();
    beaconBlockBody.setEth1_data(get_eth1_data_stub(postState, compute_epoch_of_slot(slot)));
    beaconBlockBody.setDeposits(deposits);
    beaconBlockBody.setAttestations(attestations);
    return new BeaconBlock(slot, parentBlockRoot, stateRoot, beaconBlockBody, BLSSignature.empty());
  }

  private static class ProposedBlock {
    private final BeaconBlock block;
    private final Validator proposer;
    private final int proposerIndex;

    private ProposedBlock(
        final BeaconBlock block, final Validator proposer, final int proposerIndex) {
      this.block = block;
      this.proposer = proposer;
      this.proposerIndex = proposerIndex;
    }

    public BeaconBlock getBlock() {
      return block;
    }

    public Validator getProposer() {
      return proposer;
    }

    public int getProposerIndex() {
      return proposerIndex;
    }
  }
}
