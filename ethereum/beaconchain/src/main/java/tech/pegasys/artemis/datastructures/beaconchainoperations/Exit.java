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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes48;

public class Exit {

  private UnsignedLong slot;
  private UnsignedLong validator_index;
  private Bytes48[] signature;

  public Exit(UnsignedLong slot, UnsignedLong validator_index, Bytes48[] signature) {
    this.slot = slot;
    this.validator_index = validator_index;
    this.signature = signature;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public UnsignedLong getValidator_index() {
    return validator_index;
  }

  public void setValidator_index(UnsignedLong validator_index) {
    this.validator_index = validator_index;
  }

  public Bytes48[] getSignature() {
    return signature;
  }

  public void setSignature(Bytes48[] signature) {
    this.signature = signature;
  }
}
