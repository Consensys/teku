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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;

public class Deposit {
  public final List<Bytes32> proof;
  public final DepositData data;

  public Deposit(tech.pegasys.teku.datastructures.operations.Deposit deposit) {
    this.proof = deposit.getProof().stream().collect(Collectors.toList());
    this.data = new DepositData(deposit.getData());
  }

  @JsonCreator
  public Deposit(
      @JsonProperty("proof") final List<Bytes32> proof,
      @JsonProperty("data") final DepositData data) {
    this.proof = proof;
    this.data = data;
  }

  public tech.pegasys.teku.datastructures.operations.Deposit asInternalDeposit() {
    return new tech.pegasys.teku.datastructures.operations.Deposit(
        SSZVector.createMutable(proof, Bytes32.class), data.asInternalDepositData());
  }
}
