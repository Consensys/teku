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

import java.math.BigInteger;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.javatuples.Pair;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

/**
 * Used as first encrypted message sent in response to WHOAREYOU {@link WhoAreYouPacket}. Contains
 * an authentication header completing the handshake.
 *
 * <p>Format:<code>
 * message-packet = tag || auth-header || message
 * auth-header = [auth-tag, id-nonce, auth-scheme-name, ephemeral-pubkey, auth-response]
 * auth-scheme-name = "gcm"</code>
 *
 * <p>auth-response-pt is encrypted with a separate key, the auth-resp-key, using an all-zero nonce.
 * This is safe because only one message is ever encrypted with this key.
 *
 * <p><code>auth-response = aesgcm_encrypt(auth-resp-key, zero-nonce, auth-response-pt, "")
 * zero-nonce = 12 zero bytes
 * auth-response-pt = [version, id-nonce-sig, node-record]
 * version = 5
 * id-nonce-sig = sign(static-node-key, sha256("discovery-id-nonce" || id-nonce))
 * static-node-key = the private key used for node record identity
 * node-record = record of sender OR [] if enr-seq in WHOAREYOU != current seq
 * message = aesgcm_encrypt(initiator-key, auth-tag, message-pt, tag || auth-header)
 * message-pt = message-type || message-data
 * auth-tag = AES-GCM nonce, 12 random bytes unique to message</code>
 */
@SuppressWarnings({"DefaultCharset"})
public class AuthHeaderMessagePacket extends AbstractPacket {
  public static final String AUTH_SCHEME_NAME = "gcm";
  public static final Bytes DISCOVERY_ID_NONCE = Bytes.wrap("discovery-id-nonce".getBytes());
  private static final Bytes ZERO_NONCE = Bytes.wrap(new byte[12]);
  private EphemeralPubKeyDecoded decodedEphemeralPubKeyPt = null;
  private MessagePtDecoded decodedMessagePt = null;

  public AuthHeaderMessagePacket(Bytes bytes) {
    super(bytes);
  }

  public static AuthHeaderMessagePacket create(
      Bytes tag, Bytes authHeader, Bytes messageCipherText) {
    return new AuthHeaderMessagePacket(Bytes.concatenate(tag, authHeader, messageCipherText));
  }

  public static Bytes createIdNonceMessage(Bytes idNonce, Bytes ephemeralPubkey) {
    Bytes message = Bytes.concatenate(DISCOVERY_ID_NONCE, idNonce, ephemeralPubkey);
    return message;
  }

  public static Bytes signIdNonce(Bytes idNonce, Bytes staticNodeKey, Bytes ephemeralPubkey) {
    Bytes signed =
        Functions.sign(
            staticNodeKey, Functions.hash(createIdNonceMessage(idNonce, ephemeralPubkey)));
    return signed;
  }

  public static byte[] createAuthMessagePt(Bytes idNonceSig, @Nullable NodeRecord nodeRecord) {
    return RlpEncoder.encode(
        new RlpList(
            RlpString.create(5),
            RlpString.create(idNonceSig.toArray()),
            nodeRecord == null ? new RlpList() : nodeRecord.asRlp()));
  }

  public static Bytes encodeAuthResponse(byte[] authResponsePt, Bytes authResponseKey) {
    return Functions.aesgcm_encrypt(
        authResponseKey, ZERO_NONCE, Bytes.wrap(authResponsePt), Bytes.EMPTY);
  }

  public static Bytes encodeAuthHeaderRlp(
      Bytes authTag, Bytes idNonce, Bytes ephemeralPubkey, Bytes authResponse) {
    RlpList authHeaderRlp =
        new RlpList(
            RlpString.create(authTag.toArray()),
            RlpString.create(idNonce.toArray()),
            RlpString.create(AUTH_SCHEME_NAME.getBytes()),
            RlpString.create(ephemeralPubkey.toArray()),
            RlpString.create(authResponse.toArray()));
    return Bytes.wrap(RlpEncoder.encode(authHeaderRlp));
  }

  public static AuthHeaderMessagePacket create(
      Bytes homeNodeId,
      Bytes destNodeId,
      Bytes authResponseKey,
      Bytes idNonce,
      Bytes staticNodeKey,
      @Nullable NodeRecord nodeRecord,
      Bytes ephemeralPubkey,
      Bytes authTag,
      Bytes initiatorKey,
      DiscoveryMessage message) {
    Bytes tag = Packet.createTag(homeNodeId, destNodeId);
    Bytes idNonceSig = signIdNonce(idNonce, staticNodeKey, ephemeralPubkey);
    byte[] authResponsePt = createAuthMessagePt(idNonceSig, nodeRecord);
    Bytes authResponse = encodeAuthResponse(authResponsePt, authResponseKey);
    Bytes authHeader = encodeAuthHeaderRlp(authTag, idNonce, ephemeralPubkey, authResponse);
    Bytes encryptedData = Functions.aesgcm_encrypt(initiatorKey, authTag, message.getBytes(), tag);
    return create(tag, authHeader, encryptedData);
  }

  public void verify(Bytes expectedIdNonce, Bytes remoteNodePubKey) {
    verifyDecode();
    assert expectedIdNonce.equals(getIdNonce());
    assert Functions.verifyECDSASignature(
        getIdNonceSig(),
        Functions.hash(createIdNonceMessage(getIdNonce(), getEphemeralPubkey())),
        remoteNodePubKey);
  }

  public Bytes getHomeNodeId(Bytes destNodeId) {
    verifyDecode();
    return Functions.hash(destNodeId).xor(decodedEphemeralPubKeyPt.tag);
  }

  public Bytes getAuthTag() {
    verifyDecode();
    return decodedEphemeralPubKeyPt.authTag;
  }

  public Bytes getIdNonce() {
    verifyDecode();
    return decodedEphemeralPubKeyPt.idNonce;
  }

  public Bytes getEphemeralPubkey() {
    verifyEphemeralPubKeyDecode();
    return decodedEphemeralPubKeyPt.ephemeralPubkey;
  }

  public Bytes getIdNonceSig() {
    verifyDecode();
    return decodedMessagePt.idNonceSig;
  }

  public NodeRecord getNodeRecord() {
    verifyDecode();
    return decodedMessagePt.nodeRecord;
  }

  public DiscoveryMessage getMessage() {
    verifyDecode();
    return decodedMessagePt.message;
  }

  private void verifyEphemeralPubKeyDecode() {
    if (decodedEphemeralPubKeyPt == null) {
      throw new RuntimeException("You should run decodeEphemeralPubKey before!");
    }
  }

  private void verifyDecode() {
    if (decodedEphemeralPubKeyPt == null || decodedMessagePt == null) {
      throw new RuntimeException("You should run decodeEphemeralPubKey and decodeMessage before!");
    }
  }

  public void decodeEphemeralPubKey() {
    if (decodedEphemeralPubKeyPt != null) {
      return;
    }
    EphemeralPubKeyDecoded blank = new EphemeralPubKeyDecoded();
    blank.tag = Bytes.wrap(getBytes().slice(0, 32));
    Pair<RlpList, Bytes> decodeRes = RlpUtil.decodeFirstList(getBytes().slice(32));
    blank.messageEncrypted = decodeRes.getValue1();
    RlpList authHeaderParts = (RlpList) decodeRes.getValue0().getValues().get(0);
    // [auth-tag, id-nonce, auth-scheme-name, ephemeral-pubkey, auth-response]
    blank.authTag = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(0)).getBytes());
    blank.idNonce = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(1)).getBytes());
    assert AUTH_SCHEME_NAME.equals(
        new String(((RlpString) authHeaderParts.getValues().get(2)).getBytes()));
    blank.ephemeralPubkey = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(3)).getBytes());
    blank.authResponse = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(4)).getBytes());
    this.decodedEphemeralPubKeyPt = blank;
  }

  /** Run {@link AuthHeaderMessagePacket#decodeEphemeralPubKey()} before second part */
  public void decodeMessage(
      Bytes readKey, Bytes authResponseKey, NodeRecordFactory nodeRecordFactory) {
    if (decodedEphemeralPubKeyPt == null) {
      throw new RuntimeException("Run decodeEphemeralPubKey() before");
    }
    if (decodedMessagePt != null) {
      return;
    }
    MessagePtDecoded blank = new MessagePtDecoded();
    Bytes authResponsePt =
        Functions.aesgcm_decrypt(
            authResponseKey, ZERO_NONCE, decodedEphemeralPubKeyPt.authResponse, Bytes.EMPTY);
    RlpList authResponsePtParts =
        (RlpList) RlpDecoder.decode(authResponsePt.toArray()).getValues().get(0);
    assert BigInteger.valueOf(5)
        .equals(((RlpString) authResponsePtParts.getValues().get(0)).asPositiveBigInteger());
    blank.idNonceSig = Bytes.wrap(((RlpString) authResponsePtParts.getValues().get(1)).getBytes());
    RlpList nodeRecordDataList = ((RlpList) authResponsePtParts.getValues().get(2));
    blank.nodeRecord =
        nodeRecordDataList.getValues().isEmpty()
            ? null
            : nodeRecordFactory.fromRlpList(nodeRecordDataList);
    blank.message =
        new DiscoveryV5Message(
            Functions.aesgcm_decrypt(
                readKey,
                decodedEphemeralPubKeyPt.authTag,
                decodedEphemeralPubKeyPt.messageEncrypted,
                decodedEphemeralPubKeyPt.tag));
    this.decodedMessagePt = blank;
  }

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder("AuthHeaderMessagePacket{");
    if (decodedEphemeralPubKeyPt != null) {
      res.append("tag=")
          .append(decodedEphemeralPubKeyPt.tag)
          .append(", authTag=")
          .append(decodedEphemeralPubKeyPt.authTag)
          .append(", idNonce=")
          .append(decodedEphemeralPubKeyPt.idNonce)
          .append(", ephemeralPubkey=")
          .append(decodedEphemeralPubKeyPt.ephemeralPubkey);
    }
    if (decodedMessagePt != null) {
      res.append(", idNonceSig=")
          .append(decodedMessagePt.idNonceSig)
          .append(", nodeRecord=")
          .append(decodedMessagePt.nodeRecord)
          .append(", message=")
          .append(decodedMessagePt.message);
    }
    res.append('}');
    return res.toString();
  }

  private static class EphemeralPubKeyDecoded {
    private Bytes tag;
    private Bytes authTag;
    private Bytes idNonce;
    private Bytes ephemeralPubkey;
    private Bytes authResponse;
    private Bytes messageEncrypted;
  }

  private static class MessagePtDecoded {
    private Bytes idNonceSig;
    private NodeRecord nodeRecord;
    private DiscoveryMessage message;
  }
}
