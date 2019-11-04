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

package org.ethereum.beacon.discovery.enr;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.javatuples.Pair;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

// import tech.pegasys.artemis.util.bytes.Bytes8;
// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.uint.UInt64;

public class NodeRecordFactory {
  public static final NodeRecordFactory DEFAULT =
      new NodeRecordFactory(new EnrSchemeV4Interpreter());
  Map<EnrScheme, EnrSchemeInterpreter> interpreters = new HashMap<>();

  public NodeRecordFactory(EnrSchemeInterpreter... enrSchemeInterpreters) {
    for (EnrSchemeInterpreter enrSchemeInterpreter : enrSchemeInterpreters) {
      interpreters.put(enrSchemeInterpreter.getScheme(), enrSchemeInterpreter);
    }
  }

  @SafeVarargs
  public final NodeRecord createFromValues(
      EnrScheme enrScheme, UInt64 seq, Bytes signature, Pair<String, Object>... fieldKeyPairs) {
    return createFromValues(enrScheme, seq, signature, Arrays.asList(fieldKeyPairs));
  }

  public NodeRecord createFromValues(
      EnrScheme enrScheme, UInt64 seq, Bytes signature, List<Pair<String, Object>> fieldKeyPairs) {

    EnrSchemeInterpreter enrSchemeInterpreter = interpreters.get(enrScheme);
    if (enrSchemeInterpreter == null) {
      throw new RuntimeException(
          String.format("No ethereum record interpreter found for identity scheme %s", enrScheme));
    }

    return NodeRecord.fromValues(enrSchemeInterpreter, seq, signature, fieldKeyPairs);
  }

  public NodeRecord fromBase64(String enrBase64) {
    return fromBytes(Base64.getUrlDecoder().decode(enrBase64));
  }

  public NodeRecord fromBytes(Bytes bytes) {
    return fromBytes(bytes.toArray());
  }

  public NodeRecord fromBytes(byte[] bytes) {
    // record    = [signature, seq, k, v, ...]
    RlpList rlpList = (RlpList) RlpDecoder.decode(bytes).getValues().get(0);
    List<RlpType> values = rlpList.getValues();
    if (values.size() < 4) {
      throw new RuntimeException(
          String.format(
              "Unable to deserialize ENR with less than 4 fields, [%s]", Bytes.wrap(bytes)));
    }
    RlpString id = (RlpString) values.get(2);
    if (!"id".equals(new String(id.getBytes()))) {
      throw new RuntimeException(
          String.format(
              "Unable to deserialize ENR with no id field at 2-3 records, [%s]",
              Bytes.wrap(bytes)));
    }

    RlpString idVersion = (RlpString) values.get(3);
    EnrScheme nodeIdentity = EnrScheme.fromString(new String(idVersion.getBytes()));
    if (nodeIdentity == null) {
      throw new RuntimeException(
          String.format(
              "Unknown node identity scheme '%s', couldn't create node record.",
              idVersion.asString()));
    }

    EnrSchemeInterpreter enrSchemeInterpreter = interpreters.get(nodeIdentity);
    if (enrSchemeInterpreter == null) {
      throw new RuntimeException(
          String.format(
              "No ethereum record interpreter found for identity scheme %s", nodeIdentity));
    }

    return NodeRecord.fromRawFields(
        enrSchemeInterpreter,
        UInt64.fromBytes( // BigEndian
            Bytes.wrap(((RlpString) values.get(1)).getBytes())),
        Bytes.wrap(((RlpString) values.get(0)).getBytes()),
        values.subList(4, values.size()));
  }
}
