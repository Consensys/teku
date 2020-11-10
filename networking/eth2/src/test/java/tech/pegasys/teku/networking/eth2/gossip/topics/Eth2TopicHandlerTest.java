/*
 * Copyright 2020 ConsenSys AG.
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

import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult;
import tech.pegasys.teku.networking.p2p.gossip.PreparedMessage;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class Eth2TopicHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0);
  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
  private final Bytes blockBytes = GossipEncoding.SSZ_SNAPPY.encode(block);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final MockEth2TopicHandler topicHandler = new MockEth2TopicHandler(asyncRunner);

  @Test
  public void handleMessage_valid() {
    topicHandler.setValidationResult(InternalValidationResult.ACCEPT);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_invalid() {
    topicHandler.setValidationResult(InternalValidationResult.REJECT);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_ignore() {
    topicHandler.setValidationResult(InternalValidationResult.IGNORE);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidBytes() {
    final Bytes invalidBytes = Bytes.fromHexString("0x0102");
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(invalidBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_decodingException() {
    topicHandler.setDeserializer(
        (b) -> {
          throw new DecodingException("oops");
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_wrappedDecodingException() {
    topicHandler.setDeserializer(
        (b) -> {
          throw new CompletionException(new DecodingException("oops"));
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_decodingExceptionWithCause() {
    topicHandler.setDeserializer(
        (b) -> {
          throw new DecodingException("oops", new RuntimeException("oops"));
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_rejectedExecution() {
    topicHandler.setMessageProcessor(
        (b, r) -> {
          throw new RejectedExecutionException("No more capacity");
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_wrappedRejectedExecution() {
    topicHandler.setMessageProcessor(
        (b, r) -> {
          throw new CompletionException(new RejectedExecutionException("No more capacity"));
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_rejectedExecutionWithRootCause() {
    topicHandler.setMessageProcessor(
        (b, r) -> {
          throw new RejectedExecutionException("No more capacity", new NullPointerException());
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_unknownError() {
    topicHandler.setMessageProcessor(
        (b, r) -> {
          throw new NullPointerException();
        });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  private static class MockEth2TopicHandler
      extends Eth2TopicHandler<SignedBeaconBlock, SignedBeaconBlock> {
    private Deserializer<SignedBeaconBlock> deserializer =
        (bytes) -> getGossipEncoding().decode(bytes, SignedBeaconBlock.class);
    private MessageProcessor messageProcessor = (b, r) -> {};
    private InternalValidationResult validationResult = InternalValidationResult.ACCEPT;

    protected MockEth2TopicHandler(final AsyncRunner asyncRunner) {
      super(asyncRunner);
    }

    public void setDeserializer(final Deserializer<SignedBeaconBlock> deserializer) {
      this.deserializer = deserializer;
    }

    public void setMessageProcessor(final MessageProcessor messageProcessor) {
      this.messageProcessor = messageProcessor;
    }

    public void setValidationResult(InternalValidationResult result) {
      this.validationResult = result;
    }

    @Override
    protected SignedBeaconBlock wrapMessage(final SignedBeaconBlock deserialized) {
      return deserialized;
    }

    @Override
    protected SafeFuture<InternalValidationResult> validateData(final SignedBeaconBlock message) {
      return SafeFuture.completedFuture(validationResult);
    }

    @Override
    protected void processMessage(
        final SignedBeaconBlock message, final InternalValidationResult internalValidationResult) {
      messageProcessor.process(message, internalValidationResult);
    }

    @Override
    public SignedBeaconBlock deserialize(PreparedMessage message) throws DecodingException {
      return deserializer.deserialize(message);
    }

    @Override
    public Bytes4 getForkDigest() {
      return Bytes4.fromHexString("0x01020304");
    }

    @Override
    public GossipEncoding getGossipEncoding() {
      return GossipEncoding.SSZ_SNAPPY;
    }

    @Override
    public String getTopicName() {
      return "test";
    }

    @Override
    public Class<SignedBeaconBlock> getValueType() {
      return SignedBeaconBlock.class;
    }
  }

  private interface MessageProcessor {
    void process(
        final SignedBeaconBlock message, final InternalValidationResult internalValidationResult);
  }

  private interface Deserializer<T> {
    T deserialize(final PreparedMessage bytes) throws DecodingException;
  }
}
