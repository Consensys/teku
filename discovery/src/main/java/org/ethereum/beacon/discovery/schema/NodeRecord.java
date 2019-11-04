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

package org.ethereum.beacon.discovery.schema;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.javatuples.Pair;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

// import tech.pegasys.artemis.util.bytes.Bytes32;
// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Ethereum Node Record
 *
 * <p>Node record as described in <a href="https://eips.ethereum.org/EIPS/eip-778">EIP-778</a>
 */
public class NodeRecord {
  // Compressed secp256k1 public key, 33 bytes
  public static String FIELD_PKEY_SECP256K1 = "secp256k1";
  // IPv4 address
  public static String FIELD_IP_V4 = "ip";
  // TCP port, integer
  public static String FIELD_TCP_V4 = "tcp";
  // UDP port, integer
  public static String FIELD_UDP_V4 = "udp";
  // IPv6 address
  public static String FIELD_IP_V6 = "ip6";
  // IPv6-specific TCP port
  public static String FIELD_TCP_V6 = "tcp6";
  // IPv6-specific UDP port
  public static String FIELD_UDP_V6 = "udp6";

  private UInt64 seq;
  // Signature
  private Bytes signature;
  // optional fields
  private Map<String, Object> fields = new HashMap<>();

  private EnrSchemeInterpreter enrSchemeInterpreter;

  private NodeRecord(EnrSchemeInterpreter enrSchemeInterpreter, UInt64 seq, Bytes signature) {
    this.seq = seq;
    this.signature = signature;
    this.enrSchemeInterpreter = enrSchemeInterpreter;
  }

  private NodeRecord() {}

  public static NodeRecord fromValues(
      EnrSchemeInterpreter enrSchemeInterpreter,
      UInt64 seq,
      Bytes signature,
      List<Pair<String, Object>> fieldKeyPairs) {
    NodeRecord nodeRecord = new NodeRecord(enrSchemeInterpreter, seq, signature);
    fieldKeyPairs.forEach(objects -> nodeRecord.set(objects.getValue0(), objects.getValue1()));
    return nodeRecord;
  }

  public static NodeRecord fromRawFields(
      EnrSchemeInterpreter enrSchemeInterpreter,
      UInt64 seq,
      Bytes signature,
      List<RlpType> rawFields) {
    NodeRecord nodeRecord = new NodeRecord(enrSchemeInterpreter, seq, signature);
    for (int i = 0; i < rawFields.size(); i += 2) {
      String key = new String(((RlpString) rawFields.get(i)).getBytes());
      nodeRecord.set(key, enrSchemeInterpreter.decode(key, (RlpString) rawFields.get(i + 1)));
    }
    return nodeRecord;
  }

  public String asBase64() {
    return new String(Base64.getUrlEncoder().encode(serialize().toArray()));
  }

  public EnrScheme getIdentityScheme() {
    return enrSchemeInterpreter.getScheme();
  }

  public void set(String key, Object value) {
    fields.put(key, value);
  }

  public Object get(String key) {
    return fields.get(key);
  }

  public UInt64 getSeq() {
    return seq;
  }

  public void setSeq(UInt64 seq) {
    this.seq = seq;
  }

  public Bytes getSignature() {
    return signature;
  }

  public void setSignature(Bytes signature) {
    this.signature = signature;
  }

  public Set<String> getKeys() {
    return new HashSet<>(fields.keySet());
  }

  public Object getKey(String key) {
    return fields.get(key);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NodeRecord that = (NodeRecord) o;
    return Objects.equal(seq, that.seq)
        && Objects.equal(signature, that.signature)
        && Objects.equal(fields, that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(seq, signature, fields);
  }

  public void verify() {
    enrSchemeInterpreter.verify(this);
  }

  public Bytes serialize() {
    return serialize(true);
  }

  public Bytes serialize(boolean withSignature) {
    assert getSeq() != null;
    // content   = [seq, k, v, ...]
    // signature = sign(content)
    // record    = [signature, seq, k, v, ...]
    List<RlpType> values = new ArrayList<>();
    if (withSignature) {
      values.add(RlpString.create(getSignature().toArray()));
    }
    values.add(RlpString.create(getSeq().toBigInteger()));
    values.add(RlpString.create("id"));
    values.add(RlpString.create(getIdentityScheme().stringName()));
    for (Map.Entry<String, Object> keyPair : fields.entrySet()) {
      if (keyPair.getValue() == null) {
        continue;
      }
      values.add(RlpString.create(keyPair.getKey()));
      values.add(enrSchemeInterpreter.encode(keyPair.getKey(), keyPair.getValue()));
    }
    byte[] bytes = RlpEncoder.encode(new RlpList(values));
    assert bytes.length <= 300;
    return Bytes.wrap(bytes);
  }

  public Bytes getNodeId() {
    return enrSchemeInterpreter.getNodeId(this);
  }

  @Override
  public String toString() {
    return "NodeRecordV4{"
        + "publicKey="
        + fields.get(FIELD_PKEY_SECP256K1)
        + ", ipV4address="
        + fields.get(FIELD_IP_V4)
        + ", udpPort="
        + fields.get(FIELD_UDP_V4)
        + '}';
  }
}
