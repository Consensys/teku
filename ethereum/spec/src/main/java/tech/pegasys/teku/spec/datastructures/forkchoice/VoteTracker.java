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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class VoteTracker {

  public static final VoteTracker DEFAULT =
      new VoteTracker(Bytes32.ZERO, Bytes32.ZERO, UInt64.ZERO);

  private final Bytes32 currentRoot;
  private final Bytes32 nextRoot;
  private final UInt64 nextEpoch;
  private final boolean nextEquivocating;
  private final boolean currentEquivocating;

  public VoteTracker(final Bytes32 currentRoot, final Bytes32 nextRoot, final UInt64 nextEpoch) {
    this(currentRoot, nextRoot, nextEpoch, false, false);
  }

  public VoteTracker(
      Bytes32 currentRoot,
      Bytes32 nextRoot,
      UInt64 nextEpoch,
      boolean nextEquivocating,
      boolean currentEquivocating) {
    this.currentRoot = currentRoot;
    this.nextRoot = nextRoot;
    this.nextEpoch = nextEpoch;
    this.nextEquivocating = nextEquivocating;
    this.currentEquivocating = currentEquivocating;
  }

  public Bytes32 getCurrentRoot() {
    return currentRoot;
  }

  public Bytes32 getNextRoot() {
    return nextRoot;
  }

  public UInt64 getNextEpoch() {
    return nextEpoch;
  }

  public boolean isNextEquivocating() {
    return nextEquivocating;
  }

  public boolean isCurrentEquivocating() {
    return currentEquivocating;
  }

  public boolean isEquivocating() {
    return nextEquivocating || currentEquivocating;
  }

  public VoteTracker createNextEquivocating() {
    return new VoteTracker(currentRoot, nextRoot, nextEpoch, true, false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VoteTracker that = (VoteTracker) o;
    return nextEquivocating == that.nextEquivocating
        && currentEquivocating == that.currentEquivocating
        && Objects.equals(currentRoot, that.currentRoot)
        && Objects.equals(nextRoot, that.nextRoot)
        && Objects.equals(nextEpoch, that.nextEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currentRoot, nextRoot, nextEpoch, nextEquivocating, currentEquivocating);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("currentRoot", currentRoot)
        .add("nextRoot", nextRoot)
        .add("nextEpoch", nextEpoch)
        .add("nextEquivocating", nextEquivocating)
        .add("currentEquivocating", currentEquivocating)
        .toString();
  }
}
