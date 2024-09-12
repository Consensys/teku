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

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;

@SuppressWarnings("unused")
public class ExecutionPayloadHeaderFactory {

  private final Spec spec;
  private final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager;

  public ExecutionPayloadHeaderFactory(
      final Spec spec,
      final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager) {
    this.spec = spec;
    this.executionLayerBlockProductionManager = executionLayerBlockProductionManager;
  }

  // EIP7732 TODO: implement
  public SafeFuture<ExecutionPayloadHeader> createUnsignedHeader(
      final BeaconState state, final UInt64 slot, final BLSPublicKey builderPublicKey) {
    int builderIndex =
        BeaconStateCache.getTransitionCaches(state)
            .getValidatorIndexCache()
            .getValidatorIndex(state, builderPublicKey)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "There is no index assigned to a builder with a public key "
                            + builderPublicKey));
    return SafeFuture.completedFuture(null);
  }
}
