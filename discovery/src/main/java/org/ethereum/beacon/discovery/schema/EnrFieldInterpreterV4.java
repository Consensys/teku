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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class EnrFieldInterpreterV4 implements EnrFieldInterpreter {
  public static EnrFieldInterpreterV4 DEFAULT = new EnrFieldInterpreterV4();

  private Map<String, Function<RlpString, Object>> fieldDecoders = new HashMap<>();

  public EnrFieldInterpreterV4() {
    fieldDecoders.put(EnrFieldV4.PKEY_SECP256K1, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(
        EnrField.ID, rlpString -> IdentitySchema.fromString(new String(rlpString.getBytes())));
    fieldDecoders.put(EnrField.IP_V4, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(EnrField.TCP_V4, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(EnrField.UDP_V4, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(EnrField.IP_V6, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(EnrField.TCP_V6, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(EnrField.UDP_V6, rlpString -> rlpString.asPositiveBigInteger().intValue());
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
  public RlpType encode(String key, Object object) {
    return RlpUtil.encode(
        object,
        o ->
            String.format(
                "Couldn't encode field %s with value %s: no serializer found.", key, object));
  }
}
