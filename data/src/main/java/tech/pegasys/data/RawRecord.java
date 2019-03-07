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


public class RawRecord {

  private BeaconState state;
  private BeaconBlock block;
  public RawRecord() {
    
  }

  public RawRecord(BeaconState state, BeaconBlock block) {
    this.state = state;
    this.block = block;
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
        if (o == this)
            return true;
        if (!(o instanceof Record)) {
            return false;
        }
        Record record = (Record) o;
        return Objects.equals(state, record.state) && Objects.equals(block, record.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, block);
  }

  @Override
  public String toString() {
    return "{" +
      " state='" + getState() + "'" +
      ", block='" + getBlock() + "'" +
      "}";
  }
}