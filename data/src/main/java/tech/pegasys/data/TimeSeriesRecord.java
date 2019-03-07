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


public class TimeSeriesRecord {

  private Long time;
  private Long slot;
  private Bytes blockRoot;
  private Bytes stateRoot;
  private Bytes parentBlockRoot;

  public TimeSeriesRecord(Long time, Long slot, Bytes blockRoot, Bytes stateRoot, Bytes parentBlockRoot) {
    this.time = time;
    this.slot = slot;
    this.blockRoot = blockRoot;
    this.stateRoot = stateRoot;
    this.parentBlockRoot = parentBlockRoot;
  }

  public Long getTime() {
    return this.time;
  }

  public void setTime(Long time) {
    this.time = time;
  }

  public Long getSlot() {
    return this.slot;
  }

  public void setSlot(Long slot) {
    this.slot = slot;
  }

  public Bytes getBlockRoot() {
    return this.blockRoot;
  }

  public void setBlockRoot(Bytes blockRoot) {
    this.blockRoot = blockRoot;
  }

  public Bytes getStateRoot() {
    return this.stateRoot;
  }

  public void setStateRoot(Bytes stateRoot) {
    this.stateRoot = stateRoot;
  }

  public Bytes getParentBlockRoot() {
    return this.parentBlockRoot;
  }

  public void setParentBlockRoot(Bytes parentBlockRoot) {
    this.parentBlockRoot = parentBlockRoot;
  }

  @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof TimeSeriesRecord)) {
            return false;
        }
        TimeSeriesRecord timeSeriesRecord = (TimeSeriesRecord) o;
        return Objects.equals(time, timeSeriesRecord.time) && Objects.equals(slot, timeSeriesRecord.slot) && Objects.equals(blockRoot, timeSeriesRecord.blockRoot) && Objects.equals(stateRoot, timeSeriesRecord.stateRoot) && Objects.equals(parentBlockRoot, timeSeriesRecord.parentBlockRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(time, slot, blockRoot, stateRoot, parentBlockRoot);
  }

  @Override
  public String toString() {
    return "{" +
      " time='" + getTime() + "'" +
      ", slot='" + getSlot() + "'" +
      ", blockRoot='" + getBlockRoot() + "'" +
      ", stateRoot='" + getStateRoot() + "'" +
      ", parentBlockRoot='" + getParentBlockRoot() + "'" +
      "}";
  }

  public TimeSeriesRecord() {
    
  }

  public TimeSeriesRecord(BeaconState state, BeaconBlock block) {

  }

 
}