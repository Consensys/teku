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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;

public class Fork {

  private UnsignedLong previous_version;
  private UnsignedLong current_version;
  private UnsignedLong epoch;

  public Fork(UnsignedLong previous_version, UnsignedLong current_version, UnsignedLong epoch) {
    this.previous_version = previous_version;
    this.current_version = current_version;
    this.epoch = epoch;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getPrevious_version() {
    return previous_version;
  }

  public void setPrevious_version(UnsignedLong previous_version) {
    this.previous_version = previous_version;
  }

  public UnsignedLong getCurrent_version() {
    return current_version;
  }

  public void setCurrent_version(UnsignedLong current_version) {
    this.current_version = current_version;
  }

  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }
}
