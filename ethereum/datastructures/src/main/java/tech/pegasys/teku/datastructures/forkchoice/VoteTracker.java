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

package tech.pegasys.teku.datastructures.forkchoice;

import com.google.common.base.Objects;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class VoteTracker implements SimpleOffsetSerializable {

  private Bytes32 currentRoot;
  private Bytes32 nextRoot;
  private UInt64 nextEpoch;

  public VoteTracker(Bytes32 currentRoot, Bytes32 nextRoot, UInt64 nextEpoch) {
    this.currentRoot = currentRoot;
    this.nextRoot = nextRoot;
    this.nextEpoch = nextEpoch;
  }

  public VoteTracker copy() {
    return new VoteTracker(currentRoot, nextRoot, nextEpoch);
  }

  public static VoteTracker Default() {
    return new VoteTracker(Bytes32.ZERO, Bytes32.ZERO, UInt64.ZERO);
  }

  @Override
  public int getSSZFieldCount() {
    return 3;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(getCurrentRoot())),
        SSZ.encode(writer -> writer.writeFixedBytes(getNextRoot())),
        SSZ.encodeUInt64(getNextEpoch().longValue()));
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
  public String toString() {
    return "VoteTracker{"
        + "currentRoot="
        + currentRoot
        + ", nextRoot="
        + nextRoot
        + ", nextEpoch="
        + nextEpoch
        + '}';
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getCurrentRoot(), getNextRoot(), getNextEpoch());
  }

  /** ****************** * GETTERS & SETTERS * * ******************* */
  public void setCurrentRoot(Bytes32 currentRoot) {
    this.currentRoot = currentRoot;
  }

  public void setNextRoot(Bytes32 nextRoot) {
    this.nextRoot = nextRoot;
  }

  public void setNextEpoch(UInt64 nextEpoch) {
    this.nextEpoch = nextEpoch;
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
}
