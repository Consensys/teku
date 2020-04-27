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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import java.util.OptionalInt;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.BeaconBlocksByRootRequestMessageEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.SimpleOffsetSszEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.StringSszEncoder;

public interface RpcEncoding {
  RpcEncoding SSZ =
      new LengthPrefixedEncoding(
          "ssz",
          RpcPayloadEncoders.builder()
              .withEncoder(
                  BeaconBlocksByRootRequestMessage.class,
                  new BeaconBlocksByRootRequestMessageEncoder())
              .withEncoder(String.class, new StringSszEncoder())
              .defaultEncoderProvider(SimpleOffsetSszEncoder::new)
              .build());

  <T> Bytes encode(T message);

  <T> T decode(Bytes message, Class<T> clazz) throws RpcException;

  String getName();

  OptionalInt getMessageLength(Bytes message) throws RpcException;
}
