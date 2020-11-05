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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.pubsub.ValidationResult;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.Eth2GossipMessage;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SingleAttestationTopicHandlerTest {

  private static final int SUBNET_ID = 1;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);

  @SuppressWarnings("unchecked")
  private final GossipedItemConsumer<ValidateableAttestation> gossipedAttestationConsumer =
      mock(GossipedItemConsumer.class);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData =
      MemoryOnlyRecentChainData.create(mock(EventBus.class));
  private final AttestationValidator attestationValidator = mock(AttestationValidator.class);
  private final SingleAttestationTopicHandler topicHandler =
      new tech.pegasys.teku.networking.eth2.gossip.topics.SingleAttestationTopicHandler(
          asyncRunner,
          gossipEncoding,
          dataStructureUtil.randomForkInfo(),
          SUBNET_ID,
          attestationValidator,
          gossipedAttestationConsumer);

  @BeforeEach
  public void setup() {
    BeaconChainUtil.initializeStorage(recentChainData, validatorKeys);
  }

  private Eth2GossipMessage createMessageStub(Bytes decompressedPayload) {
    return new Eth2GossipMessage("/test/topic", Bytes.EMPTY, () -> decompressedPayload);
  }

  @Test
  public void handleMessage_valid() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = recentChainData.getHeadBlockAndState().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(
            attestationGenerator.validAttestation(blockAndState));
    when(attestationValidator.validate(attestation, SUBNET_ID))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    verify(gossipedAttestationConsumer).forward(attestation);
  }

  @Test
  public void handleMessage_ignored() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = recentChainData.getHeadBlockAndState().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(
            attestationGenerator.validAttestation(blockAndState));
    when(attestationValidator.validate(attestation, SUBNET_ID))
        .thenReturn(SafeFuture.completedFuture(IGNORE));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(gossipedAttestationConsumer, never()).forward(attestation);
  }

  @Test
  public void handleMessage_saveForFuture() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = recentChainData.getHeadBlockAndState().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(
            attestationGenerator.validAttestation(blockAndState));
    when(attestationValidator.validate(attestation, SUBNET_ID))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
    verify(gossipedAttestationConsumer).forward(attestation);
  }

  @Test
  public void handleMessage_invalid() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = recentChainData.getHeadBlockAndState().orElseThrow();
    final ValidateableAttestation attestation =
        ValidateableAttestation.fromAttestation(
            attestationGenerator.validAttestation(blockAndState));
    when(attestationValidator.validate(attestation, SUBNET_ID))
        .thenReturn(SafeFuture.completedFuture(REJECT));
    final Bytes serialized = gossipEncoding.encode(attestation.getAttestation());

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verify(gossipedAttestationConsumer, never()).forward(attestation);
  }

  @Test
  public void handleMessage_invalidAttestation_invalidSSZ() {
    final Bytes serialized = Bytes.fromHexString("0x3456");

    final SafeFuture<ValidationResult> result = topicHandler
        .handleMessage(createMessageStub(serialized));
    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final ForkInfo forkInfo = mock(ForkInfo.class);
    when(forkInfo.getForkDigest()).thenReturn(forkDigest);
    final SingleAttestationTopicHandler topicHandler =
        new SingleAttestationTopicHandler(
            asyncRunner,
            gossipEncoding,
            forkInfo,
            0,
            attestationValidator,
            gossipedAttestationConsumer);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/beacon_attestation_0/ssz_snappy");
  }
}
