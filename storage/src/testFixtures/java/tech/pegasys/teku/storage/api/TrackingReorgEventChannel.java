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

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TrackingReorgEventChannel implements ReorgEventChannel {
  private final List<ReorgEvent> reorgEvents = new ArrayList<>();

  @Override
  public void reorgOccurred(
      final Bytes32 bestBlockRoot,
      final UInt64 bestSlot,
      final Bytes32 bestStateRoot,
      final Bytes32 oldBestBlockRoot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot) {
    reorgEvents.add(
        new ReorgEvent(
            bestBlockRoot,
            bestSlot,
            bestStateRoot,
            oldBestBlockRoot,
            oldBestStateRoot,
            commonAncestorSlot));
  }

  public List<ReorgEvent> getReorgEvents() {
    return reorgEvents;
  }

  public static class ReorgEvent {
    private final Bytes32 newBestBlockRoot;
    private final UInt64 bestSlot;
    private final Bytes32 newBestStateRoot;
    private final Bytes32 oldBestBlockRoot;
    private final Bytes32 oldBestStateRoot;
    private final UInt64 commonAncestorSlot;

    public ReorgEvent(
        final Bytes32 newBestBlockRoot,
        final UInt64 bestSlot,
        final Bytes32 newBestStateRoot,
        final Bytes32 oldBestBlockRoot,
        final Bytes32 oldBestStateRoot,
        final UInt64 commonAncestorSlot) {
      this.newBestBlockRoot = newBestBlockRoot;
      this.bestSlot = bestSlot;
      this.newBestStateRoot = newBestStateRoot;
      this.oldBestBlockRoot = oldBestBlockRoot;
      this.oldBestStateRoot = oldBestStateRoot;
      this.commonAncestorSlot = commonAncestorSlot;
    }

    public Bytes32 getNewBestBlockRoot() {
      return newBestBlockRoot;
    }

    public UInt64 getBestSlot() {
      return bestSlot;
    }

    public Bytes32 getOldBestBlockRoot() {
      return oldBestBlockRoot;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final ReorgEvent that = (ReorgEvent) o;
      return Objects.equals(newBestBlockRoot, that.newBestBlockRoot)
          && Objects.equals(bestSlot, that.bestSlot)
          && Objects.equals(newBestStateRoot, that.newBestStateRoot)
          && Objects.equals(oldBestBlockRoot, that.oldBestBlockRoot)
          && Objects.equals(oldBestStateRoot, that.oldBestStateRoot)
          && Objects.equals(commonAncestorSlot, that.commonAncestorSlot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          newBestBlockRoot,
          bestSlot,
          newBestStateRoot,
          oldBestBlockRoot,
          oldBestStateRoot,
          commonAncestorSlot);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("newBestBlockRoot", newBestBlockRoot)
          .add("bestSlot", bestSlot)
          .add("newBestStateRoot", newBestStateRoot)
          .add("oldBestBlockRoot", oldBestBlockRoot)
          .add("oldBestStateRoot", oldBestStateRoot)
          .add("commonAncestorSlot", commonAncestorSlot)
          .toString();
    }
  }
}
