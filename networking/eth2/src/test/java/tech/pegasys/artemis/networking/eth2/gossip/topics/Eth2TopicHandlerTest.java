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

package tech.pegasys.artemis.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

public class Eth2TopicHandlerTest {

  private final EventBus eventBus = new EventBus();
  private final MockTopicHandler topicHandler = spy(new MockTopicHandler(eventBus));
  private final Bytes message = Bytes.fromHexString("0x01");

  @Test
  public void handleMessage_valid() {
    topicHandler.setShouldValidate(true);
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(true);
  }

  @Test
  public void handleMessage_invalid() {
    topicHandler.setShouldValidate(false);
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
  }

  @Test
  public void handleMessage_whenDeserializationFails() {
    topicHandler.setShouldValidate(true);
    doThrow(new SSZException("whoops")).when(topicHandler).deserialize(message);
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
  }

  @Test
  public void handleMessage_whenDeserializeReturnsNull() {
    topicHandler.setShouldValidate(true);
    doReturn(null).when(topicHandler).deserialize(message);
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
  }

  @Test
  public void handleMessage_whenValidationThrowsAnException() {
    topicHandler.setShouldValidate(true);
    doThrow(new RuntimeException("whoops")).when(topicHandler).validateData(any());
    final boolean result = topicHandler.handleMessage(message);

    assertThat(result).isEqualTo(false);
  }

  private static class MockTopicHandler extends Eth2TopicHandler<Attestation> {
    private static final String topic = "testing";
    private static final Attestation deserialized = DataStructureUtil.randomAttestation(1);

    private boolean shouldValidate = true;

    protected MockTopicHandler(final EventBus eventBus) {
      super(eventBus);
    }

    @Override
    public String getTopic() {
      return topic;
    }

    @Override
    protected Attestation deserialize(final Bytes bytes) throws SSZException {
      return deserialized;
    }

    @Override
    protected boolean validateData(final Attestation attestation) {
      return shouldValidate;
    }

    public void setShouldValidate(final boolean shouldValidate) {
      this.shouldValidate = shouldValidate;
    }
  }
}
