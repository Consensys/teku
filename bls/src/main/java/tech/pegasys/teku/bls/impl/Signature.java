/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;

public interface Signature {

  Bytes toBytes();

  Bytes toBytesCompressed();

  boolean verify(List<PublicKeyMessagePair> keysToMessages);

  default boolean verify(List<PublicKey> publicKeys, Bytes message) {
    return verify(
        publicKeys.stream()
            .map(pk -> new PublicKeyMessagePair(pk, message))
            .collect(Collectors.toList()));
  }

  default boolean verify(PublicKey publicKey, Bytes message) {
    return verify(Collections.singletonList(publicKey), message);
  }
}
