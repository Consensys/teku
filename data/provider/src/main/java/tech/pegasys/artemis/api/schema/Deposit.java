/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.api.schema;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.ssz.SSZTypes.SSZVector;

public class Deposit {
  public final List<Bytes32> proof;
  public final DepositData data;

  public Deposit(tech.pegasys.artemis.datastructures.operations.Deposit deposit) {
    this.proof = deposit.getProof().stream().collect(Collectors.toList());
    this.data = new DepositData(deposit.getData());
  }

  public tech.pegasys.artemis.datastructures.operations.Deposit asInternalDeposit() {
    return new tech.pegasys.artemis.datastructures.operations.Deposit(
        SSZVector.createMutable(proof, Bytes32.class), data.asInternalDepositData());
  }
}
