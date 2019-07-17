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

package tech.pegasys.artemis.networking.p2p.hobbits.gossip;

import java.math.BigInteger;

/** Representation of a Gossip message that was received from a remote peer. */
public final class GossipMessage {

  private final int method;
  private final String topic;
  private final BigInteger timestamp;
  private final byte[] messageHash;
  private final byte[] hash;
  private final byte[] body;
  private final int length;

  public GossipMessage(
      int method,
      String topic,
      BigInteger timestamp,
      byte[] messageHash,
      byte[] hash,
      byte[] body,
      int length) {
    this.method = method;
    this.topic = topic;
    this.timestamp = timestamp;
    this.messageHash = messageHash;
    this.hash = hash;
    this.body = body;
    this.length = length;
  }

  /** @return the method used by the Gossip call. */
  public int method() {
    return method;
  }

  /** @return the Gossip topic. */
  public String getTopic() {
    return topic;
  }

  /** @return the Gossip topic. */
  public BigInteger getTimestamp() {
    return timestamp;
  }

  /** @return the messageHash used by the Gossip call. */
  public byte[] messageHash() {
    return messageHash;
  }

  /** @return the hashSignature used by the Gossip call. */
  public byte[] hash() {
    return hash;
  }

  /** @return the body of the message if present */
  public byte[] body() {
    return body;
  }

  /** @return the length of the message in bytes */
  public int length() {
    return length;
  }
}
