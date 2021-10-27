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
  private final Bytes32 oldBestStateRoot;
  private final UInt64 commonAncestorSlot;

  private final Bytes32 commonAncestorRoot;
  private final UInt64 newChainHeadSlot;
  private final UInt64 oldBestChainHeadSlot;

  public ReorgContext(
      final Bytes32 oldBestBlockRoot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot,
      final UInt64 newChainHeadSlot,
      final UInt64 oldBestChainHeadSlot) {
    this.oldBestBlockRoot = oldBestBlockRoot;
    this.oldBestStateRoot = oldBestStateRoot;
    this.commonAncestorSlot = commonAncestorSlot;

    this.commonAncestorRoot = commonAncestorRoot;
    this.newChainHeadSlot = newChainHeadSlot;
    this.oldBestChainHeadSlot = oldBestChainHeadSlot;
  }

  public Bytes32 getOldBestBlockRoot() {
    return oldBestBlockRoot;
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

  public UInt64 getNewChainHeadSlot() {
    return newChainHeadSlot;
  }

  public UInt64 getOldBestChainHeadSlot() {
    return oldBestChainHeadSlot;
  }

  public static Optional<ReorgContext> of(
      final Bytes32 oldBestBlockRoot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot,
      final UInt64 newChainHeadSlot,
      final UInt64 oldBestChainHeadSlot) {
    return Optional.of(
        new ReorgContext(
            oldBestBlockRoot,
            oldBestStateRoot,
            commonAncestorSlot,
            commonAncestorRoot,
            newChainHeadSlot,
            oldBestChainHeadSlot));
  }

  public static Optional<ReorgContext> empty() {
    return Optional.empty();
  }
}
