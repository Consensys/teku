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

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.Utils;
import org.javatuples.Pair;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class NodeRecordFactory {
  public static final NodeRecordFactory DEFAULT =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
  Map<IdentitySchema, IdentitySchemaInterpreter> interpreters = new HashMap<>();

  public NodeRecordFactory(IdentitySchemaInterpreter... identitySchemaInterpreters) {
    for (IdentitySchemaInterpreter identitySchemaInterpreter : identitySchemaInterpreters) {
      interpreters.put(identitySchemaInterpreter.getScheme(), identitySchemaInterpreter);
    }
  }

  @SafeVarargs
  public final NodeRecord createFromValues(
      UInt64 seq, Bytes signature, Pair<String, Object>... fieldKeyPairs) {
    return createFromValues(seq, signature, Arrays.asList(fieldKeyPairs));
  }

  public NodeRecord createFromValues(
      UInt64 seq, Bytes signature, List<Pair<String, Object>> fieldKeyPairs) {
    Pair<String, Object> schemePair = null;
    for (Pair<String, Object> pair : fieldKeyPairs) {
      if (EnrField.ID.equals(pair.getValue0())) {
        schemePair = pair;
        break;
      }
    }
    if (schemePair == null) {
      throw new RuntimeException("ENR scheme is not defined in key-value pairs");
    }

    IdentitySchemaInterpreter identitySchemaInterpreter = interpreters.get(schemePair.getValue1());
    if (identitySchemaInterpreter == null) {
      throw new RuntimeException(
          String.format(
              "No ethereum record interpreter found for identity scheme %s",
              schemePair.getValue1()));
    }

    return NodeRecord.fromValues(identitySchemaInterpreter, seq, signature, fieldKeyPairs);
  }

  public NodeRecord fromBase64(String enrBase64) {
    return fromBytes(Base64.getUrlDecoder().decode(enrBase64));
  }

  public NodeRecord fromBytes(Bytes bytes) {
    return fromBytes(bytes.toArray());
  }

  public NodeRecord fromRlpList(RlpList rlpList) {
    List<RlpType> values = rlpList.getValues();
    if (values.size() < 4) {
      throw new RuntimeException(
          String.format("Unable to deserialize ENR with less than 4 fields, [%s]", values));
    }

    // TODO: repair as id is not first now
    IdentitySchema nodeIdentity = null;
    boolean idFound = false;
    for (int i = 2; i < values.size(); i += 2) {
      RlpString id = (RlpString) values.get(i);
      if (!"id".equals(new String(id.getBytes()))) {
        continue;
      }

      RlpString idVersion = (RlpString) values.get(i + 1);
      nodeIdentity = IdentitySchema.fromString(new String(idVersion.getBytes()));
      if (nodeIdentity == null) { // no interpreter for such id
        throw new RuntimeException(
            String.format(
                "Unknown node identity scheme '%s', couldn't create node record.",
                idVersion.asString()));
      }
      idFound = true;
      break;
    }
    if (!idFound) { // no `id` key-values
      throw new RuntimeException("Unknown node identity scheme, not defined in record ");
    }

    IdentitySchemaInterpreter identitySchemaInterpreter = interpreters.get(nodeIdentity);
    if (identitySchemaInterpreter == null) {
      throw new RuntimeException(
          String.format(
              "No Ethereum record interpreter found for identity scheme %s", nodeIdentity));
    }

    return NodeRecord.fromRawFields(
        identitySchemaInterpreter,
        UInt64.fromBytes(Utils.leftPad(Bytes.wrap(((RlpString) values.get(1)).getBytes()), 8)),
        Bytes.wrap(((RlpString) values.get(0)).getBytes()),
        values.subList(2, values.size()));
  }

  public NodeRecord fromBytes(byte[] bytes) {
    // record    = [signature, seq, k, v, ...]
    RlpList rlpList = (RlpList) RlpDecoder.decode(bytes).getValues().get(0);
    return fromRlpList(rlpList);
  }
}
