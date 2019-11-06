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

package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.util.Functions;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpString;

/**
 * Used when handshake is completed as a {@link DiscoveryMessage} authenticated container
 *
 * <p>Format:<code>
 * message-packet = tag || rlp_bytes(auth-tag) || message
 * message = aesgcm_encrypt(initiator-key, auth-tag, message-pt, tag)
 * message-pt = message-type || message-data</code>
 */
public class MessagePacket extends AbstractPacket {
  private MessagePacketDecoded decoded = null;
  private Bytes initiatorKey = null;

  public MessagePacket(Bytes bytes) {
    super(bytes);
  }

  public static MessagePacket create(
      Bytes homeNodeId,
      Bytes destNodeId,
      Bytes authTag,
      Bytes initiatorKey,
      DiscoveryMessage message) {
    Bytes tag = Packet.createTag(homeNodeId, destNodeId);
    byte[] authTagBytesRlp = RlpEncoder.encode(RlpString.create(authTag.toArray()));
    Bytes authTagEncoded = Bytes.wrap(authTagBytesRlp);
    Bytes encryptedData = Functions.aesgcm_encrypt(initiatorKey, authTag, message.getBytes(), tag);
    return new MessagePacket(Bytes.concatenate(tag, authTagEncoded, encryptedData));
  }

  public void verify(Bytes expectedAuthTag) {}

  public Bytes getHomeNodeId(Bytes destNodeId) {
    verifyDecode();
    return Functions.hash(destNodeId).xor(decoded.tag, MutableBytes.create(decoded.tag.size()));
  }

  public Bytes getAuthTag() {
    if (decoded == null) {
      return Bytes.wrap(
          ((RlpString) RlpDecoder.decode(getBytes().slice(32, 13).toArray()).getValues().get(0))
              .getBytes());
    }
    return decoded.authTag;
  }

  public DiscoveryMessage getMessage() {
    verifyDecode();
    return decoded.message;
  }

  private void verifyDecode() {
    if (decoded == null) {
      throw new RuntimeException("You should decode packet at first!");
    }
  }

  public void decode(Bytes initiatorKey) {
    if (decoded != null) {
      return;
    }
    MessagePacketDecoded blank = new MessagePacketDecoded();
    blank.tag = Bytes.wrap(getBytes().slice(0, 32));
    blank.authTag =
        Bytes.wrap(
            ((RlpString) RlpDecoder.decode(getBytes().slice(32, 13).toArray()).getValues().get(0))
                .getBytes());
    blank.message =
        new DiscoveryV5Message(
            Functions.aesgcm_decrypt(initiatorKey, blank.authTag, getBytes().slice(45), blank.tag));
    this.decoded = blank;
  }

  @Override
  public String toString() {
    if (decoded != null) {
      return "MessagePacket{"
          + "tag="
          + decoded.tag
          + ", authTag="
          + decoded.authTag
          + ", message="
          + decoded.message
          + '}';
    } else {
      return "MessagePacket{" + getBytes() + '}';
    }
  }

  private static class MessagePacketDecoded {
    private Bytes tag;
    private Bytes authTag;
    private DiscoveryMessage message;
  }
}
