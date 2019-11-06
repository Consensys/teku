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

import static org.ethereum.beacon.discovery.schema.NodeRecord.FIELD_IP_V4;
import static org.ethereum.beacon.discovery.schema.NodeRecord.FIELD_IP_V6;
import static org.ethereum.beacon.discovery.schema.NodeRecord.FIELD_PKEY_SECP256K1;
import static org.ethereum.beacon.discovery.schema.NodeRecord.FIELD_TCP_V4;
import static org.ethereum.beacon.discovery.schema.NodeRecord.FIELD_TCP_V6;
import static org.ethereum.beacon.discovery.schema.NodeRecord.FIELD_UDP_V4;
import static org.ethereum.beacon.discovery.schema.NodeRecord.FIELD_UDP_V6;
import static org.ethereum.beacon.discovery.util.CryptoUtil.sha256;
import static org.ethereum.beacon.discovery.util.Utils.extractBytesFromUnsignedBigInt;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.Arrays;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.rlp.RlpString;

public class EnrSchemeV4Interpreter implements EnrSchemeInterpreter {

  private Map<String, Function<RlpString, Object>> fieldDecoders = new HashMap<>();

  public EnrSchemeV4Interpreter() {
    fieldDecoders.put(FIELD_PKEY_SECP256K1, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(FIELD_IP_V4, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(FIELD_TCP_V4, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(FIELD_UDP_V4, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(FIELD_IP_V6, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(FIELD_TCP_V6, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(FIELD_UDP_V6, rlpString -> rlpString.asPositiveBigInteger().intValue());
  }

  @Override
  public void verify(NodeRecord nodeRecord) {
    EnrSchemeInterpreter.super.verify(nodeRecord);
    if (nodeRecord.get(FIELD_PKEY_SECP256K1) == null) {
      throw new RuntimeException(
          String.format(
              "Field %s not exists but required for scheme %s", FIELD_PKEY_SECP256K1, getScheme()));
    }
    Bytes pubKey = (Bytes) nodeRecord.get(FIELD_PKEY_SECP256K1);
    ECDSASignature ecdsaSignature =
        new ECDSASignature(
            new BigInteger(1, nodeRecord.getSignature().slice(0, 32).toArray()),
            new BigInteger(1, nodeRecord.getSignature().slice(32).toArray()));
    byte[] msgHash = Hash.sha3(nodeRecord.serialize(false).toArray());
    for (int recId = 0; recId < 4; ++recId) {
      BigInteger calculatedPubKey = Sign.recoverFromSignature(1, ecdsaSignature, msgHash);
      if (calculatedPubKey == null) {
        continue;
      }
      if (Arrays.areEqual(pubKey.toArray(), extractBytesFromUnsignedBigInt(calculatedPubKey))) {
        return;
      }
    }
    assert false;
  }

  @Override
  public EnrScheme getScheme() {
    return EnrScheme.V4;
  }

  @Override
  public Bytes getNodeId(NodeRecord nodeRecord) {
    verify(nodeRecord);
    Object key = nodeRecord.getKey(FIELD_PKEY_SECP256K1);
    if (key instanceof Bytes) {
      return sha256((Bytes) key);
    }
    return sha256((Bytes) key);
  }

  @Override
  public Object decode(String key, RlpString rlpString) {
    Function<RlpString, Object> fieldDecoder = fieldDecoders.get(key);
    if (fieldDecoder == null) {
      throw new RuntimeException(String.format("No decoder found for field `%s`", key));
    }
    return fieldDecoder.apply(rlpString);
  }

  @Override
  public RlpString encode(String key, Object object) {
    if (object instanceof Bytes) {
      return fromBytes((Bytes) object);
    } else if (object instanceof Bytes) {
      return fromBytes((Bytes) object);
    } else if (object instanceof Number) {
      return fromNumber((Number) object);
    } else if (object == null) {
      return RlpString.create(new byte[0]);
    } else {
      throw new RuntimeException(
          String.format(
              "Couldn't serialize node record field %s with value %s: no serializer found.",
              key, object));
    }
  }

  private RlpString fromNumber(Number number) {
    if (number instanceof BigInteger) {
      return RlpString.create((BigInteger) number);
    } else if (number instanceof Long) {
      return RlpString.create((Long) number);
    } else if (number instanceof Integer) {
      return RlpString.create((Integer) number);
    } else {
      throw new RuntimeException(
          String.format("Couldn't serialize number %s : no serializer found.", number));
    }
  }

  private RlpString fromBytes(Bytes bytes) {
    return RlpString.create(bytes.toArray());
  }
}
