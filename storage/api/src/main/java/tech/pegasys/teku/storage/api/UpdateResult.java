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

package tech.pegasys.teku.storage.api;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;

public class UpdateResult {

  public static final UpdateResult EMPTY = new UpdateResult(Optional.empty());

  private final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload;

  public UpdateResult(
      final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload) {
    this.finalizedOptimisticTransitionPayload = finalizedOptimisticTransitionPayload;
  }

  /**
   * Get the slot and execution payload summary from the block specified in FinalizedChainData. If
   * no transition block root is specified this will always be empty.
   */
  public Optional<SlotAndExecutionPayloadSummary> getFinalizedOptimisticTransitionPayload() {
    return finalizedOptimisticTransitionPayload;
  }
}
