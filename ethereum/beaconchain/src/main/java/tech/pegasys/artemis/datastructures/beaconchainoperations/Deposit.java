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
import net.consensys.cava.bytes.Bytes32;

public class Deposit {

  private Bytes32[] merkle_branch;
  private UnsignedLong merkle_tree_index;
  private DepositData deposit_data;

  public Deposit(
      Bytes32[] merkle_branch, UnsignedLong merkle_tree_index, DepositData deposit_data) {
    this.merkle_branch = merkle_branch;
    this.merkle_tree_index = merkle_tree_index;
    this.deposit_data = deposit_data;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32[] getMerkle_branch() {
    return merkle_branch;
  }

  public void setMerkle_branch(Bytes32[] merkle_branch) {
    this.merkle_branch = merkle_branch;
  }

  public UnsignedLong getMerkle_tree_index() {
    return merkle_tree_index;
  }

  public void setMerkle_tree_index(UnsignedLong merkle_tree_index) {
    this.merkle_tree_index = merkle_tree_index;
  }

  public DepositData getDeposit_data() {
    return deposit_data;
  }

  public void setDeposit_data(DepositData deposit_data) {
    this.deposit_data = deposit_data;
  }
}
