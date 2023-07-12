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

package tech.pegasys.teku.spec.datastructures.execution.versions.deneb;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;

public interface ExecutionPayloadDeneb extends ExecutionPayload, ExecutionPayloadCapella {

  static ExecutionPayloadDeneb required(final ExecutionPayload payload) {
    return payload
        .toVersionDeneb()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Deneb execution payload but got "
                        + payload.getClass().getSimpleName()));
  }

  UInt64 getDataGasUsed();

  UInt64 getExcessDataGas();

  @Override
  default Optional<ExecutionPayloadDeneb> toVersionDeneb() {
    return Optional.of(this);
  }

  @Override
  default SpecMilestone getMilestone() {
    return SpecMilestone.DENEB;
  }
}
