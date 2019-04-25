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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventResponse;

public class Deposit extends AbstractEvent<DepositEventResponse> implements DepositEvent {

  private Bytes data;
  private Bytes merkel_tree_index;

  public Deposit(DepositEventResponse response) {
    super(response);
    data = Bytes.wrap(response.data);
    merkel_tree_index = Bytes.wrap(response.merkle_tree_index);
  }

  public Bytes getData() {
    return data;
  }

  public Bytes getMerkle_tree_index() {
    return merkel_tree_index;
  }

  @Override
  public String toString() {
    return data.toString() + "\n" + merkel_tree_index.toString();
  }
}
