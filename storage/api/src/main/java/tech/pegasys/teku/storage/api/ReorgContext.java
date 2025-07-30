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
    boolean isLateBlockReorg) {

  public static Optional<ReorgContext> of(
      final Bytes32 oldBestBlockRoot,
      final UInt64 oldBestBlockSlot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot,
      final boolean isLateBlockReorg) {
    return Optional.of(
        new ReorgContext(
            oldBestBlockRoot,
            oldBestBlockSlot,
            oldBestStateRoot,
            commonAncestorSlot,
            commonAncestorRoot,
            isLateBlockReorg));
  }

  public static Optional<ReorgContext> empty() {
    return Optional.empty();
  }
}
