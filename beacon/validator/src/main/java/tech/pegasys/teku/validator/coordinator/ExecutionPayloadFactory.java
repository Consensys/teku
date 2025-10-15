/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

public interface ExecutionPayloadFactory {

  ExecutionPayloadFactory NOOP =
      new ExecutionPayloadFactory() {
        @Override
        public SafeFuture<ExecutionPayloadEnvelope> createUnsignedExecutionPayload(
            final UInt64 builderIndex, final BeaconBlockAndState blockAndState) {
          return new SafeFuture<>();
        }

        @Override
        public List<DataColumnSidecar> createDataColumnSidecars(
            final SignedExecutionPayloadEnvelope signedExecutionPayload) {
          return List.of();
        }
      };

  SafeFuture<ExecutionPayloadEnvelope> createUnsignedExecutionPayload(
      UInt64 builderIndex, BeaconBlockAndState blockAndState);

  List<DataColumnSidecar> createDataColumnSidecars(
      SignedExecutionPayloadEnvelope signedExecutionPayload);
}
