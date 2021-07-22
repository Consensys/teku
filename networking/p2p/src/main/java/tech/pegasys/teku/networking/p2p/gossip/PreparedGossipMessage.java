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

package tech.pegasys.teku.networking.p2p.gossip;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;

/**
 * Semi-processed raw gossip message which can supply Gossip 'message-id'
 *
 * @see TopicHandler#prepareMessage(Bytes)
 */
public interface PreparedGossipMessage {

  /**
   * Returns the Gossip 'message-id' If the 'message-id' calculation is resource consuming operation
   * is should performed lazily by implementation class
   */
  Bytes getMessageId();

  /** @return Returns the decoded message content */
  DecodedMessageResult getDecodedMessage();

  Bytes getOriginalMessage();

  class DecodedMessageResult {
    private final Optional<Bytes> decodedMessage;
    private final Optional<Throwable> decodingException;

    private DecodedMessageResult(
        final Optional<Bytes> decodedMessage, final Optional<Throwable> decodingException) {
      this.decodedMessage = decodedMessage;
      this.decodingException = decodingException;
    }

    public static DecodedMessageResult successful(final Bytes uncompressedMessage) {
      return new DecodedMessageResult(Optional.of(uncompressedMessage), Optional.empty());
    }

    public static DecodedMessageResult failed(final Throwable decodingException) {
      return new DecodedMessageResult(Optional.empty(), Optional.of(decodingException));
    }

    public static DecodedMessageResult failed() {
      return new DecodedMessageResult(Optional.empty(), Optional.empty());
    }

    public Optional<Bytes> getDecodedMessage() {
      return decodedMessage;
    }

    public Bytes getDecodedMessageOrElseThrow() {
      return decodedMessage.orElseThrow(this::getGossipDecodingException);
    }

    private GossipDecodingException getGossipDecodingException() {
      return decodingException
          .map(GossipDecodingException::new)
          .orElseGet(() -> new GossipDecodingException("Failed to decode gossip message"));
    }

    public Optional<Throwable> getDecodingException() {
      return decodingException;
    }
  }

  class GossipDecodingException extends RuntimeException {
    public GossipDecodingException(final String message) {
      super(message);
    }

    public GossipDecodingException(final Throwable cause) {
      super(cause);
    }
  }
}
