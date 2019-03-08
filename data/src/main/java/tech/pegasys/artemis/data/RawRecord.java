/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.data;

import java.util.Objects;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;

public class RawRecord {

  private Long nodeTime;
  private Long nodeSlot;
  private BeaconState state;
  private BeaconBlock block;

  public RawRecord() {}

  public RawRecord(Long nodeTime, Long nodeSlot, BeaconState state, BeaconBlock block) {
    this.nodeTime = nodeTime;
    this.nodeSlot = nodeSlot;
    this.state = state;
    this.block = block;
  }

  public Long getNodeTime() {
    return this.nodeTime;
  }

  public void setNodeTime(Long nodeTime) {
    this.nodeTime = nodeTime;
  }

  public Long getNodeSlot() {
    return this.nodeSlot;
  }

  public void setNodeSlot(Long nodeSlot) {
    this.nodeSlot = nodeSlot;
  }

  public BeaconState getState() {
    return this.state;
  }

  public void setState(BeaconState state) {
    this.state = state;
  }

  public BeaconBlock getBlock() {
    return this.block;
  }

  public void setBlock(BeaconBlock block) {
    this.block = block;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof RawRecord)) {
      return false;
    }
    RawRecord rawRecord = (RawRecord) o;
    return Objects.equals(nodeTime, rawRecord.nodeTime)
        && Objects.equals(nodeSlot, rawRecord.nodeSlot)
        && Objects.equals(state, rawRecord.state)
        && Objects.equals(block, rawRecord.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeTime, nodeSlot, state, block);
  }
}
