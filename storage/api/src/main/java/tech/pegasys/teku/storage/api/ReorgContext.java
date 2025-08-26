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

package tech.pegasys.teku.storage.api;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record ReorgContext(
    Bytes32 oldBestBlockRoot,
    UInt64 oldBestBlockSlot,
    Bytes32 oldBestStateRoot,
    UInt64 commonAncestorSlot,
    Bytes32 commonAncestorRoot,
    Optional<LateBlockReorgContext> lateBlockReorgContext) {

  public static Optional<ReorgContext> of(
      final Bytes32 oldBestBlockRoot,
      final UInt64 oldBestBlockSlot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot,
      final Optional<LateBlockReorgContext> lateBlockReorgContext) {
    return Optional.of(
        new ReorgContext(
            oldBestBlockRoot,
            oldBestBlockSlot,
            oldBestStateRoot,
            commonAncestorSlot,
            commonAncestorRoot,
            lateBlockReorgContext));
  }

  public static Optional<ReorgContext> empty() {
    return Optional.empty();
  }

  public record LateBlockReorgContext(
      Bytes32 lateBlockRoot,
      UInt64 lateBlockSlot,
      Bytes32 lateBlockParentRoot,
      LateBlockReorgStage stage) {}

  public enum LateBlockReorgStage {
    PREPARATION, // when head is set to late block's parent
    COMPLETION, // when we import our locally produced block reorging the late block
    FAILED // when we fail to generate a block and we end up importing a block building on top of
    // the late block which then becomes canonical
  }
}
