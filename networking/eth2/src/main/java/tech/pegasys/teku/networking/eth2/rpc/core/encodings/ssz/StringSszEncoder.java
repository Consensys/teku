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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;

public class StringSszEncoder implements RpcPayloadEncoder<String> {

  @Override
  public Bytes encode(final String message) {
    return Bytes.wrap(message.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String decode(final Bytes message) {
    return new String(message.toArray(), StandardCharsets.UTF_8);
  }
}
