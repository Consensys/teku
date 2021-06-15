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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.SingleAttestationTopicHandler;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SingleAttestationTopicHandlerTest
    extends AbstractTopicHandlerTest<ValidateableAttestation> {

  private static final int SUBNET_ID = 1;
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);

  @Override
  protected Eth2TopicHandler<?> createHandler(final Bytes4 forkDigest) {
    return SingleAttestationTopicHandler.createHandler(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkDigest,
        GossipTopicName.getAttestationSubnetTopicName(SUBNET_ID),
        SUBNET_ID);
  }

  @Test
  public void handleMessage_valid() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromNetwork(
            attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_ignored() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromNetwork(
            attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_saveForFuture() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromNetwork(
            attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalid() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromNetwork(
            attestationGenerator.validAttestation(blockAndState), SUBNET_ID);
    when(processor.process(attestation))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.REJECT));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_invalidAttestation_invalidSSZ() {
    final Bytes serialized = Bytes.fromHexString("0x3456");

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final String topicName = GossipTopicName.getAttestationSubnetTopicName(0);
    Eth2TopicHandler<?> topicHandler =
        SingleAttestationTopicHandler.createHandler(
            recentChainData, asyncRunner, processor, gossipEncoding, forkDigest, topicName, 0);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/beacon_attestation_0/ssz_snappy");
  }
}
