/*
 * Copyright 2020 ConsenSys AG.
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

public class ReorgContext {
  private final Bytes32 oldBestBlockRoot;
  private final UInt64 oldBestBlockSlot;
  private final Bytes32 oldBestStateRoot;
  private final UInt64 commonAncestorSlot;
  private final Bytes32 commonAncestorRoot;

  public ReorgContext(
      final Bytes32 oldBestBlockRoot,
      final UInt64 oldBestBlockSlot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot) {
    this.oldBestBlockRoot = oldBestBlockRoot;
    this.oldBestBlockSlot = oldBestBlockSlot;
    this.oldBestStateRoot = oldBestStateRoot;
    this.commonAncestorSlot = commonAncestorSlot;
    this.commonAncestorRoot = commonAncestorRoot;
  }

  public Bytes32 getOldBestBlockRoot() {
    return oldBestBlockRoot;
  }

  public UInt64 getOldBestBlockSlot() {
    return oldBestBlockSlot;
  }

  public Bytes32 getOldBestStateRoot() {
    return oldBestStateRoot;
  }

  public UInt64 getCommonAncestorSlot() {
    return commonAncestorSlot;
  }

  public Bytes32 getCommonAncestorRoot() {
    return commonAncestorRoot;
  }

  public static Optional<ReorgContext> of(
      final Bytes32 oldBestBlockRoot,
      final UInt64 oldBestBlockSlot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot) {
    return Optional.of(
        new ReorgContext(
            oldBestBlockRoot,
            oldBestBlockSlot,
            oldBestStateRoot,
            commonAncestorSlot,
            commonAncestorRoot));
  }

  public static Optional<ReorgContext> empty() {
    return Optional.empty();
  }
}
