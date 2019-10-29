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

package org.ethereum.beacon.discovery.message;

import com.google.common.base.Objects;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import tech.pegasys.artemis.util.bytes.Bytes1;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

/** PONG is the reply to PING {@link PingMessage} */
public class PongMessage implements V5Message {
  // Unique request id
  private final BytesValue requestId;
  // Local ENR sequence number of sender
  private final UInt64 enrSeq;
  // 16 or 4 byte IP address of the intended recipient
  private final BytesValue recipientIp;
  // recipient UDP port, a 16-bit integer
  private final Integer recipientPort;

  public PongMessage(
      BytesValue requestId, UInt64 enrSeq, BytesValue recipientIp, Integer recipientPort) {
    this.requestId = requestId;
    this.enrSeq = enrSeq;
    this.recipientIp = recipientIp;
    this.recipientPort = recipientPort;
  }

  @Override
  public BytesValue getRequestId() {
    return requestId;
  }

  public UInt64 getEnrSeq() {
    return enrSeq;
  }

  public BytesValue getRecipientIp() {
    return recipientIp;
  }

  public Integer getRecipientPort() {
    return recipientPort;
  }

  @Override
  public BytesValue getBytes() {
    return Bytes1.intToBytes1(MessageCode.PONG.byteCode())
        .concat(
            BytesValue.wrap(
                RlpEncoder.encode(
                    new RlpList(
                        RlpString.create(requestId.extractArray()),
                        RlpString.create(enrSeq.toBI()),
                        RlpString.create(recipientIp.extractArray()),
                        RlpString.create(recipientPort)))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PongMessage that = (PongMessage) o;
    return Objects.equal(requestId, that.requestId)
        && Objects.equal(enrSeq, that.enrSeq)
        && Objects.equal(recipientIp, that.recipientIp)
        && Objects.equal(recipientPort, that.recipientPort);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, enrSeq, recipientIp, recipientPort);
  }

  @Override
  public String toString() {
    return "PongMessage{"
        + "requestId="
        + requestId
        + ", enrSeq="
        + enrSeq
        + ", recipientIp="
        + recipientIp
        + ", recipientPort="
        + recipientPort
        + '}';
  }
}
