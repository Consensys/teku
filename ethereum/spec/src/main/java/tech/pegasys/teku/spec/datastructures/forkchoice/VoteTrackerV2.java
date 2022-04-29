/*
 * Copyright 2022 ConsenSys AG.
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

public class VoteTrackerV2 implements VoteTracker {

  private final Bytes32 currentRoot;
  private final Bytes32 nextRoot;
  private final UInt64 nextEpoch;
  private final boolean nextEquivocating;
  private final boolean equivocated;

  public VoteTrackerV2(final Bytes32 currentRoot, final Bytes32 nextRoot, final UInt64 nextEpoch) {
    this(currentRoot, nextRoot, nextEpoch, false, false);
  }

  public VoteTrackerV2(
      Bytes32 currentRoot,
      Bytes32 nextRoot,
      UInt64 nextEpoch,
      boolean nextEquivocating,
      boolean equivocated) {
    this.currentRoot = currentRoot;
    this.nextRoot = nextRoot;
    this.nextEpoch = nextEpoch;
    this.nextEquivocating = nextEquivocating;
    this.equivocated = equivocated;
  }

  @Override
  public Bytes32 getCurrentRoot() {
    return currentRoot;
  }

  @Override
  public Bytes32 getNextRoot() {
    return nextRoot;
  }

  @Override
  public UInt64 getNextEpoch() {
    return nextEpoch;
  }

  @Override
  public Version getVersion() {
    return Version.V2;
  }

  @Override
  public boolean isMarkedToEquivocate() {
    return nextEquivocating;
  }

  @Override
  public boolean isEquivocated() {
    return equivocated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VoteTrackerV2 that = (VoteTrackerV2) o;
    return nextEquivocating == that.nextEquivocating
        && equivocated == that.equivocated
        && Objects.equals(currentRoot, that.currentRoot)
        && Objects.equals(nextRoot, that.nextRoot)
        && Objects.equals(nextEpoch, that.nextEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currentRoot, nextRoot, nextEpoch, nextEquivocating, equivocated);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("currentRoot", currentRoot)
        .add("nextRoot", nextRoot)
        .add("nextEpoch", nextEpoch)
        .add("nextEquivocating", nextEquivocating)
        .add("equivocated", equivocated)
        .toString();
  }
}
