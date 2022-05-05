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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;

public class DepositV1 {
  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = Bytes32Deserializer.class)
  public final List<Bytes32> proof;

  public final DepositDataV1 data;

  public DepositV1(tech.pegasys.teku.spec.datastructures.operations.Deposit deposit) {
    this.proof = deposit.getProof().streamUnboxed().collect(Collectors.toList());
    this.data = new DepositDataV1(deposit.getData());
  }

  @JsonCreator
  public DepositV1(
      @JsonProperty("proof") final List<Bytes32> proof,
      @JsonProperty("data") final DepositDataV1 data) {
    this.proof = proof;
    this.data = data;
  }

  public tech.pegasys.teku.spec.datastructures.operations.Deposit asInternalDeposit() {
    return new tech.pegasys.teku.spec.datastructures.operations.Deposit(
        tech.pegasys.teku.spec.datastructures.operations.Deposit.SSZ_SCHEMA
            .getProofSchema()
            .of(proof),
        data.asInternalDepositData());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DepositV1)) {
      return false;
    }
    DepositV1 deposit = (DepositV1) o;
    return Objects.equals(proof, deposit.proof) && Objects.equals(data, deposit.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(proof, data);
  }
}
