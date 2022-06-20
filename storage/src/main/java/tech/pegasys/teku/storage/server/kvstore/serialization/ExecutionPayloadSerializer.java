/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public class ExecutionPayloadSerializer implements KvStoreSerializer<ExecutionPayload> {

  private final Spec spec;

  public ExecutionPayloadSerializer(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public ExecutionPayload deserialize(final byte[] data) {
    return spec.deserializeExecutionPayload(Bytes.wrap(data));
  }

  @Override
  public byte[] serialize(final ExecutionPayload value) {
    return value.sszSerialize().toArrayUnsafe();
  }
}
