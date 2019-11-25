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
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

/**
 * The WHOAREYOU packet, used during the handshake as a response to any message received from
 * unknown host
 *
 * <p>Format:<code>
 * whoareyou-packet = magic || [token, id-nonce, enr-seq]
 * magic = sha256(dest-node-id || "WHOAREYOU")
 * token = auth-tag of request
 * id-nonce = 32 random bytes
 * enr-seq = highest ENR sequence number of node A known on node B's side</code>
 */
@SuppressWarnings({"DefaultCharset"})
public class WhoAreYouPacket extends AbstractPacket {
  private static final Bytes MAGIC_BYTES = Bytes.wrap("WHOAREYOU".getBytes());
  private WhoAreYouDecoded decoded = null;

  public WhoAreYouPacket(Bytes bytes) {
    super(bytes);
  }

  /** Create a packet by converting {@code destNodeId} to a magic value */
  public static WhoAreYouPacket createFromNodeId(
      Bytes destNodeId, Bytes authTag, Bytes idNonce, UInt64 enrSeq) {
    Bytes magic = getStartMagic(destNodeId);
    return createFromMagic(magic, authTag, idNonce, enrSeq);
  }

  public static WhoAreYouPacket createFromMagic(
      Bytes magic, Bytes authTag, Bytes idNonce, UInt64 enrSeq) {
    byte[] rlpListEncoded =
        RlpEncoder.encode(
            new RlpList(
                RlpString.create(authTag.toArray()),
                RlpString.create(idNonce.toArray()),
                RlpString.create(enrSeq.toBigInteger())));
    return new WhoAreYouPacket(Bytes.concatenate(magic, Bytes.wrap(rlpListEncoded)));
  }

  /** Calculates first 32 bytes of WHOAREYOU packet */
  public static Bytes getStartMagic(Bytes destNodeId) {
    return Functions.hash(Bytes.concatenate(destNodeId, MAGIC_BYTES));
  }

  public Bytes getAuthTag() {
    decode();
    return decoded.authTag;
  }

  public Bytes getIdNonce() {
    decode();
    return decoded.idNonce;
  }

  public UInt64 getEnrSeq() {
    decode();
    return decoded.enrSeq;
  }

  public void verify(Bytes destNodeId, Bytes expectedAuthTag) {
    decode();
    assert Functions.hash(Bytes.concatenate(destNodeId, MAGIC_BYTES)).equals(decoded.magic);
    assert expectedAuthTag.equals(getAuthTag());
  }

  private synchronized void decode() {
    if (decoded != null) {
      return;
    }
    WhoAreYouDecoded blank = new WhoAreYouDecoded();
    blank.magic = Bytes.wrap(getBytes().slice(0, 32));
    RlpList payload =
        (RlpList) RlpDecoder.decode(getBytes().slice(32).toArray()).getValues().get(0);
    blank.authTag = Bytes.wrap(((RlpString) payload.getValues().get(0)).getBytes());
    blank.idNonce = Bytes.wrap(((RlpString) payload.getValues().get(1)).getBytes());
    blank.enrSeq =
        UInt64.fromBytes(
            Utils.leftPad(Bytes.wrap(((RlpString) payload.getValues().get(2)).getBytes()), 8));
    this.decoded = blank;
  }

  @Override
  public String toString() {
    if (decoded != null) {
      return "WhoAreYou{"
          + "magic="
          + decoded.magic
          + ", authTag="
          + decoded.authTag
          + ", idNonce="
          + decoded.idNonce
          + ", enrSeq="
          + decoded.enrSeq
          + '}';
    } else {
      return "WhoAreYou{" + getBytes() + '}';
    }
  }

  private static class WhoAreYouDecoded {
    private Bytes magic; // Bytes32
    private Bytes authTag;
    private Bytes idNonce; // Bytes32
    private UInt64 enrSeq;
  }
}
