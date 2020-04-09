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

package tech.pegasys.artemis.protoarray;

import com.google.common.base.Objects;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class VoteTracker {

  private Bytes32 currentRoot;
  private Bytes32 nextRoot;
  private UnsignedLong nextEpoch;

  public VoteTracker(Bytes32 currentRoot, Bytes32 nextRoot, UnsignedLong nextEpoch) {
    this.currentRoot = currentRoot;
    this.nextRoot = nextRoot;
    this.nextEpoch = nextEpoch;
  }

  public static VoteTracker Default() {
    return new VoteTracker(Bytes32.ZERO, Bytes32.ZERO, UnsignedLong.ZERO);
  }

  public void setCurrentRoot(Bytes32 currentRoot) {
    this.currentRoot = currentRoot;
  }

  public void setNextRoot(Bytes32 nextRoot) {
    this.nextRoot = nextRoot;
  }

  public void setNextEpoch(UnsignedLong nextEpoch) {
    this.nextEpoch = nextEpoch;
  }

  public Bytes32 getCurrentRoot() {
    return currentRoot;
  }

  public Bytes32 getNextRoot() {
    return nextRoot;
  }

  public UnsignedLong getNextEpoch() {
    return nextEpoch;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VoteTracker)) return false;
    VoteTracker that = (VoteTracker) o;
    return Objects.equal(getCurrentRoot(), that.getCurrentRoot())
        && Objects.equal(getNextRoot(), that.getNextRoot())
        && Objects.equal(getNextEpoch(), that.getNextEpoch());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getCurrentRoot(), getNextRoot(), getNextEpoch());
  }
}
