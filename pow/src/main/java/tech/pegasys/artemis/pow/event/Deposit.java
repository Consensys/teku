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

package tech.pegasys.artemis.pow.event;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventResponse;

import java.util.ArrayList;
import java.util.List;

public class Deposit extends AbstractEvent<DepositEventResponse> implements DepositEvent {

  private Bytes32 deposit_root;
  private Bytes data;
  private Bytes merkel_tree_index;
  private List<Bytes32> branch;

  public Deposit(DepositEventResponse response) {
    super(response);
    deposit_root = Bytes32.wrap(response.deposit_root);
    data = Bytes.wrap(response.data);
    merkel_tree_index = Bytes.wrap(response.merkle_tree_index);
    branch = new ArrayList<Bytes32>();
    response.branch.forEach(item -> branch.add(Bytes32.wrap(item.getValue())));
  }

  public Bytes32 getDeposit_root() {
    return deposit_root;
  }

  public Bytes getData() {
    return data;
  }

  public Bytes getMerkel_tree_index() {
    return merkel_tree_index;
  }

  public List<Bytes32> getBranch() {
    return branch;
  }
}
