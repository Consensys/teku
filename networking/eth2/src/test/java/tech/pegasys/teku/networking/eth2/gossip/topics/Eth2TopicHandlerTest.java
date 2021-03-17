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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class Eth2TopicHandlerTest {
  final Spec spec = SpecFactory.createMinimal();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
  private final Bytes blockBytes = GossipEncoding.SSZ_SNAPPY.encode(block);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @Test
  public void handleMessage_valid() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec, asyncRunner, (b) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Valid);
  }

  @Test
  public void handleMessage_invalid() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec, asyncRunner, (b) -> SafeFuture.completedFuture(InternalValidationResult.REJECT));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_ignore() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec, asyncRunner, (b) -> SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_invalidBytes() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec, asyncRunner, (b) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final Bytes invalidBytes = Bytes.fromHexString("0x0102");
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(invalidBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  public void handleMessage_errorWhileProcessing_decodingException() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec, asyncRunner, (b) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
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
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec, asyncRunner, (b) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
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
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec, asyncRunner, (b) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
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
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec,
            asyncRunner,
            (b) -> {
              throw new RejectedExecutionException("No more capacity");
            });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_wrappedRejectedExecution() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec,
            asyncRunner,
            (b) -> {
              throw new CompletionException(new RejectedExecutionException("No more capacity"));
            });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_rejectedExecutionWithRootCause() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec,
            asyncRunner,
            (b) -> {
              throw new RejectedExecutionException("No more capacity", new NullPointerException());
            });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  public void handleMessage_errorWhileProcessing_unknownError() {
    MockEth2TopicHandler topicHandler =
        new MockEth2TopicHandler(
            spec,
            asyncRunner,
            (b) -> {
              throw new NullPointerException();
            });

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(blockBytes));
    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  private static class MockEth2TopicHandler extends Eth2TopicHandler<SignedBeaconBlock> {
    private Deserializer<SignedBeaconBlock> deserializer;
    private static final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    private static final Bytes4 forkDigest = Bytes4.fromHexString("0x01020304");

    protected MockEth2TopicHandler(
        final Spec spec,
        final AsyncRunner asyncRunner,
        OperationProcessor<SignedBeaconBlock> processor) {
      super(
          asyncRunner,
          processor,
          gossipEncoding,
          forkDigest,
          "test",
          spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema());
      deserializer =
          (bytes) ->
              getGossipEncoding()
                  .decodeMessage(
                      bytes, spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema());
    }

    public void setDeserializer(final Deserializer<SignedBeaconBlock> deserializer) {
      this.deserializer = deserializer;
    }

    @Override
    public SignedBeaconBlock deserialize(PreparedGossipMessage message) throws DecodingException {
      return deserializer.deserialize(message);
    }

    @Override
    public Bytes4 getForkDigest() {
      return forkDigest;
    }

    @Override
    public GossipEncoding getGossipEncoding() {
      return gossipEncoding;
    }
  }

  private interface Deserializer<T> {
    T deserialize(final PreparedGossipMessage bytes) throws DecodingException;
  }
}
