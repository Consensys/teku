/*
 * Copyright 2018 ConsenSys AG.
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

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt64;

public class Deposit {

  private Hash[] merkle_branch;
  private UInt64 merkle_tree_index;
  private DepositData deposit_data;

  public Deposit(Hash[] merkle_branch, UInt64 merkle_tree_index, DepositData deposit_data) {
    this.merkle_branch = merkle_branch;
    this.merkle_tree_index = merkle_tree_index;
    this.deposit_data = deposit_data;
  }

  public Hash[] getMerkle_branch() {
    return merkle_branch;
  }

  public void setMerkle_branch(Hash[] merkle_branch) {
    this.merkle_branch = merkle_branch;
  }

  public UInt64 getMerkle_tree_index() {
    return merkle_tree_index;
  }

  public void setMerkle_tree_index(UInt64 merkle_tree_index) {
    this.merkle_tree_index = merkle_tree_index;
  }

  public DepositData getDeposit_data() {
    return deposit_data;
  }

  public void setDeposit_data(DepositData deposit_data) {
    this.deposit_data = deposit_data;
  }
}
