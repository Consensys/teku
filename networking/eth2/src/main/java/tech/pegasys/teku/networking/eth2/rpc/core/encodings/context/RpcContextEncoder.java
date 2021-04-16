/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.storage.client.RecentChainData;

public interface RpcContextEncoder<TContext, TPayload extends SszData> {

  static <T extends SszData> RpcContextEncoder<Bytes, T> noop(final SszSchema<T> schema) {
    return new NoopRpcContextEncoder<T>(schema);
  }

  static <T extends SszData> RpcContextEncoder<Bytes4, T> forkDigest(
      final Spec spec,
      final RecentChainData recentChainData,
      ForkDigestPayloadContext<T> payloadContext) {
    return new ForkDigestRpcContextEncoder<T>(spec, recentChainData, payloadContext) {};
  }

  RpcByteBufDecoder<TContext> getContextDecoder();

  TContext encodeContext(TPayload responsePayload);

  Optional<SszSchema<TPayload>> getSchemaFromContext(final TContext context);
}
