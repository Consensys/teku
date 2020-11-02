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

import static com.google.common.base.Preconditions.checkArgument;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.Eth2GossipMessage;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult;
import tech.pegasys.teku.networking.p2p.gossip.GossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.exceptions.ExceptionUtil;

public abstract class Eth2TopicHandler<T extends SimpleOffsetSerializable, TWrapped>
    implements TopicHandler {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;

  protected Eth2TopicHandler(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<ValidationResult> handleMessage(GossipMessage message) {
    checkArgument(
        message instanceof Eth2GossipMessage, "Unexpected message class: " + message.getClass());
    Eth2GossipMessage eth2GossipMessage = (Eth2GossipMessage) message;
    Optional<Bytes> decompressedPayload = eth2GossipMessage.getDecompressedPayload();
    if (decompressedPayload.isEmpty()) {
      // the message which couldn't be decompressed should fail earlier
      LOG.warn("Unexpectedly failed decompressing: " + message);
      return SafeFuture.completedFuture(ValidationResult.Invalid);
    }

    return SafeFuture.of(() -> deserialize(decompressedPayload.get()))
        .thenApply(this::wrapMessage)
        .thenCompose(
            wrapped ->
                asyncRunner.runAsync(
                    () ->
                        validateData(wrapped)
                            .thenApply(
                                internalValidation -> {
                                  processMessage(wrapped, internalValidation);
                                  return internalValidation.getGossipSubValidationResult();
                                })))
        .exceptionally(this::handleMessageProcessingError);
  }

  protected abstract TWrapped wrapMessage(T deserialized);

  protected abstract SafeFuture<InternalValidationResult> validateData(TWrapped message);

  protected abstract void processMessage(
      final TWrapped message, InternalValidationResult internalValidationResult);

  protected ValidationResult handleMessageProcessingError(Throwable err) {
    final ValidationResult response;
    if (ExceptionUtil.getCause(err, DecodingException.class).isPresent()) {
      LOG.trace("Received malformed gossip message on {}", getTopic());
      response = ValidationResult.Invalid;
    } else if (ExceptionUtil.getCause(err, RejectedExecutionException.class).isPresent()) {
      LOG.warn(
          "Discarding gossip message for topic {} because the executor queue is full", getTopic());
      response = ValidationResult.Ignore;
    } else {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), err);
      response = ValidationResult.Invalid;
    }

    return response;
  }

  public T deserialize(Bytes bytes) throws DecodingException {
    return getGossipEncoding().decode(bytes, getValueType());
  }

  public String getTopic() {
    return TopicNames.getTopic(getForkDigest(), getTopicName(), getGossipEncoding());
  }

  public abstract Bytes4 getForkDigest();

  public abstract GossipEncoding getGossipEncoding();

  public abstract String getTopicName();

  public abstract Class<T> getValueType();

  public abstract static class SimpleEth2TopicHandler<T extends SimpleOffsetSerializable>
      extends Eth2TopicHandler<T, T> {

    protected SimpleEth2TopicHandler(final AsyncRunner asyncRunner) {
      super(asyncRunner);
    }

    @Override
    public T deserialize(Bytes bytes) throws DecodingException {
      return getGossipEncoding().decode(bytes, getValueType());
    }

    @Override
    protected T wrapMessage(T deserialized) {
      return deserialized;
    }
  }
}
