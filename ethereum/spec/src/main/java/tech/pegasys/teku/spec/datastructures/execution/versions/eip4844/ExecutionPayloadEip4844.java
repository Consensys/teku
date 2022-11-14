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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;

public interface ExecutionPayloadEip4844 extends ExecutionPayload, ExecutionPayloadCapella {

  static ExecutionPayloadEip4844 required(final ExecutionPayload payload) {
    return payload
        .toVersionEip4844()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected EIP-4844 execution payload but got "
                        + payload.getClass().getSimpleName()));
  }

  UInt64 getExcessBlobs();

  @Override
  default Optional<ExecutionPayloadEip4844> toVersionEip4844() {
    return Optional.of(this);
  }
}
