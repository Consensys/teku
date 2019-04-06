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

package tech.pegasys.artemis.networking.p2p.hobbits;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;

/** Representation of a Gossip message that was received from a remote peer. */
public final class GossipMessage {

  private final GossipMethod method;
  private final Bytes32 messageHash;
  private final Bytes32 hashSignature;
  private final Bytes body;
  private final int length;

  public GossipMessage(
      GossipMethod method, Bytes32 messageHash, Bytes32 hashSignature, Bytes body, int length) {
    this.method = method;
    this.messageHash = messageHash;
    this.hashSignature = hashSignature;
    this.body = body;
    this.length = length;
  }

  /** @return the method used by the Gossip call. */
  public GossipMethod method() {
    return method;
  }

  /** @return the messageHash used by the Gossip call. */
  public Bytes32 messageHash() {
    return messageHash;
  }

  /** @return the hashSignature used by the Gossip call. */
  public Bytes32 hashSignature() {
    return hashSignature;
  }

  /** @return the body of the message if present */
  public Bytes body() {
    return body;
  }

  /** @return the length of the message in bytes */
  public int length() {
    return length;
  }
}
