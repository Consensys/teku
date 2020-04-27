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
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.INVALID;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.SAVED_FOR_FUTURE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult.VALID;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationTopicHandlerTest {

  public static final UnsignedLong SUBNET_ID = UnsignedLong.valueOf(1);
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);
  private final EventBus eventBus = mock(EventBus.class);
  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create(eventBus);
  private final AttestationValidator attestationValidator =
      Mockito.mock(AttestationValidator.class);
  private final AttestationTopicHandler topicHandler =
      new AttestationTopicHandler(eventBus, attestationValidator, SUBNET_ID);

  @BeforeEach
  public void setup() {
    BeaconChainUtil.initializeStorage(storageClient, validatorKeys);
  }

  @Test
  public void handleMessage_validAttestation() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = storageClient.getBestBlockAndState().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    when(attestationValidator.validate(attestation, SUBNET_ID)).thenReturn(VALID);
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(true);
    verify(eventBus).post(attestation);
  }

  @Test
  public void handleMessage_failsValidation() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = storageClient.getBestBlockAndState().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    when(attestationValidator.validate(attestation, SUBNET_ID)).thenReturn(INVALID);
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(attestation);
  }

  @Test
  public void handleMessage_saveForFuture() {
    final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    final BeaconBlockAndState blockAndState = storageClient.getBestBlockAndState().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    when(attestationValidator.validate(attestation, SUBNET_ID)).thenReturn(SAVED_FOR_FUTURE);
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
    verify(eventBus).post(attestation);
  }

  @Test
  public void handleMessage_invalidAttestation_invalidSSZ() {
    final Bytes serialized = Bytes.fromHexString("0x3456");

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
  }
}
