/*
 * Copyright Consensys Software Inc., 2024
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
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface ExecutionPayloadAndBlobSidecarsRevealer {

  /** 1. builder commits to a header */
  void commit(ExecutionPayloadHeader header, GetPayloadResponse getPayloadResponse);

  /** 2. builder reveals the payload */
  Optional<ExecutionPayloadEnvelope> revealExecutionPayload(
      SignedBeaconBlock block, BeaconState state);

  /** 3. builder reveals the blob sidecars */
  List<BlobSidecar> revealBlobSidecars(SignedBeaconBlock block);
}
