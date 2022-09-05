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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.Constants.GOSSIP_MAX_SIZE;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.BlockOperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class BlockTopicHandlerTest extends AbstractTopicHandlerTest<SignedBeaconBlock> {

  @Override
  protected Eth2TopicHandler<SignedBeaconBlock> createHandler(final Bytes4 forkDigest) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        GossipTopicName.BEACON_BLOCK,
        Optional.empty(),
        spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema(),
        GOSSIP_MAX_SIZE);
  }

  @Test
  public void handleMessage_validBlock() throws Exception {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = gossipEncoding.encode(block);

    when(processor.process(block))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_validFutureBlock() throws Exception {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);
    Bytes serialized = gossipEncoding.encode(block);

    when(processor.process(block))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidBlock_unknownPreState() {
    SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    Bytes serialized = gossipEncoding.encode(block);

    when(processor.process(block))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidBlock_wrongFork() {
    final Eth2TopicHandler<SignedBeaconBlock> blockHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            GossipTopicName.BEACON_BLOCK,
            Optional.of(
                new BlockOperationMilestoneValidator(
                    spec, recentChainData.getForkInfo(UInt64.ONE).orElseThrow())),
            spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema(),
            GOSSIP_MAX_SIZE);
    SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)));
    Bytes serialized = gossipEncoding.encode(block);
    final SafeFuture<ValidationResult> result =
        blockHandler.handleMessage(topicHandler.prepareMessage(serialized));
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_invalidBlock_invalidSSZ() {
    Bytes serialized = Bytes.fromHexString("0x1234");

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_invalidBlock_wrongProposer() throws Exception {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(UInt64.ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlotFromInvalidProposer(nextSlot);
    Bytes serialized = gossipEncoding.encode(block);
    beaconChainUtil.setSlot(nextSlot);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final Eth2TopicHandler<SignedBeaconBlock> topicHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            GossipTopicName.BEACON_BLOCK,
            Optional.empty(),
            spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema(),
            GOSSIP_MAX_SIZE);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/beacon_block/ssz_snappy");
  }
}
