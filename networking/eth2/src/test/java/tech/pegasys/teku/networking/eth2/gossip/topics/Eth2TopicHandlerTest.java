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

import com.google.common.base.Suppliers;
import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.client.RecentChainData;

public class Eth2TopicHandlerTest {
  private static final String TOPIC = "testing";

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = mock(EventBus.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Bytes message = Bytes.fromHexString("0x01");

  private final Attestation deserialized = dataStructureUtil.randomAttestation();
  private Supplier<Attestation> deserializer = Suppliers.ofInstance(deserialized);
  private Supplier<ValidationResult> validator = Suppliers.ofInstance(ValidationResult.VALID);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

  private MockTopicHandler topicHandler;

  @BeforeEach
  void setUp() {
    when(recentChainData.getCurrentForkInfo()).thenReturn(Optional.of(forkInfo));
    topicHandler = new MockTopicHandler(eventBus, forkInfo);
  }

  @Test
  public void handleMessage_valid() {
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(true);
    verify(eventBus).post(deserialized);
  }

  @Test
  public void handleMessage_savedForFuture() {
    validator = Suppliers.ofInstance(ValidationResult.SAVED_FOR_FUTURE);
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
    verify(eventBus).post(deserialized);
  }

  @Test
  public void handleMessage_invalid() {
    validator = Suppliers.ofInstance(ValidationResult.INVALID);
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(deserialized);
  }

  @Test
  public void handleMessage_whenDeserializationFails() {
    deserializer =
        () -> {
          throw new SSZException("whoops");
        };
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(deserialized);
  }

  @Test
  public void handleMessage_whenDeserializationThrowsUnexpectedException() {
    deserializer =
        () -> {
          throw new RuntimeException("whoops");
        };
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(deserialized);
  }

  @Test
  public void handleMessage_whenDeserializeReturnsNull() {
    deserializer = () -> null;
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(deserialized);
  }

  @Test
  public void handleMessage_whenValidationThrowsAnException() {
    validator =
        () -> {
          throw new RuntimeException("whoops");
        };
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(deserialized);
  }

  @Test
  public void returnProperTopicName() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x11223344");
    final ForkInfo forkInfo = mock(ForkInfo.class);
    when(forkInfo.getForkDigest()).thenReturn(forkDigest);
    MockTopicHandler topicHandler = new MockTopicHandler(eventBus, forkInfo);
    assertThat(topicHandler.getTopic()).isEqualTo("/eth2/11223344/testing/ssz");
  }

  private class MockTopicHandler extends Eth2TopicHandler<Attestation> {

    protected MockTopicHandler(final EventBus eventBus, final ForkInfo forkInfo) {
      super(eventBus, forkInfo);
    }

    @Override
    protected Attestation deserialize(final Bytes bytes) throws SSZException {
      return deserializer.get();
    }

    @Override
    protected ValidationResult validateData(final Attestation attestation) {
      return validator.get();
    }

    @Override
    public String getTopicName() {
      return TOPIC;
    }
  }
}
