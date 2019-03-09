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

  private Long index;
  private BeaconState headState;
  private BeaconBlock headBlock;
  private BeaconState justifiedState;
  private BeaconBlock justifiedBlock;
  private BeaconState finalizedState;
  private BeaconBlock finalizedBlock;

  public RawRecord() {}

  public RawRecord(
      Long index,
      BeaconState headState,
      BeaconBlock headBlock,
      BeaconState justifiedState,
      BeaconBlock justifiedBlock,
      BeaconState finalizedState,
      BeaconBlock finalizedBlock) {
    this.index = index;
    this.headState = headState;
    this.headBlock = headBlock;
    this.justifiedState = justifiedState;
    this.justifiedBlock = justifiedBlock;
    this.finalizedState = finalizedState;
    this.finalizedBlock = finalizedBlock;
  }

  public Long getIndex() {
    return this.index;
  }

  public void setIndex(Long index) {
    this.index = index;
  }

  public BeaconState getHeadState() {
    return this.headState;
  }

  public void setHeadState(BeaconState headState) {
    this.headState = headState;
  }

  public BeaconBlock getHeadBlock() {
    return this.headBlock;
  }

  public void setHeadBlock(BeaconBlock headBlock) {
    this.headBlock = headBlock;
  }

  public BeaconState getJustifiedState() {
    return this.justifiedState;
  }

  public void setJustifiedState(BeaconState justifiedState) {
    this.justifiedState = justifiedState;
  }

  public BeaconBlock getJustifiedBlock() {
    return this.justifiedBlock;
  }

  public void setJustifiedBlock(BeaconBlock justifiedBlock) {
    this.justifiedBlock = justifiedBlock;
  }

  public BeaconState getFinalizedState() {
    return this.finalizedState;
  }

  public void setFinalizedState(BeaconState finalizedState) {
    this.finalizedState = finalizedState;
  }

  public BeaconBlock getFinalizedBlock() {
    return this.finalizedBlock;
  }

  public void setFinalizedBlock(BeaconBlock finalizedBlock) {
    this.finalizedBlock = finalizedBlock;
  }

  public RawRecord index(Long index) {
    this.index = index;
    return this;
  }

  public RawRecord headState(BeaconState headState) {
    this.headState = headState;
    return this;
  }

  public RawRecord headBlock(BeaconBlock headBlock) {
    this.headBlock = headBlock;
    return this;
  }

  public RawRecord justifiedState(BeaconState justifiedState) {
    this.justifiedState = justifiedState;
    return this;
  }

  public RawRecord justifiedBlock(BeaconBlock justifiedBlock) {
    this.justifiedBlock = justifiedBlock;
    return this;
  }

  public RawRecord finalizedState(BeaconState finalizedState) {
    this.finalizedState = finalizedState;
    return this;
  }

  public RawRecord finalizedBlock(BeaconBlock finalizedBlock) {
    this.finalizedBlock = finalizedBlock;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof RawRecord)) {
      return false;
    }
    RawRecord rawRecord = (RawRecord) o;
    return Objects.equals(index, rawRecord.index)
        && Objects.equals(headState, rawRecord.headState)
        && Objects.equals(headBlock, rawRecord.headBlock)
        && Objects.equals(justifiedState, rawRecord.justifiedState)
        && Objects.equals(justifiedBlock, rawRecord.justifiedBlock)
        && Objects.equals(finalizedState, rawRecord.finalizedState)
        && Objects.equals(finalizedBlock, rawRecord.finalizedBlock);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        index,
        headState,
        headBlock,
        justifiedState,
        justifiedBlock,
        finalizedState,
        finalizedBlock);
  }
}
