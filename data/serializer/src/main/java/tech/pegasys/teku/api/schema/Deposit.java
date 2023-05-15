/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.api.schema.DepositData.DEPOSIT_DATA_TYPE;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class Deposit {
  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32))
  public List<Bytes32> proof;

  public DepositData data;

  public static DeserializableTypeDefinition<Deposit> DEPOSIT_TYPE =
      DeserializableTypeDefinition.object(Deposit.class)
          .initializer(Deposit::new)
          .withField(
              "proof",
              new DeserializableListTypeDefinition<>(CoreTypes.BYTES32_TYPE),
              Deposit::getProof,
              Deposit::setProof)
          .withField("data", DEPOSIT_DATA_TYPE, Deposit::getData, Deposit::setData)
          .build();

  public Deposit() {}

  public Deposit(tech.pegasys.teku.spec.datastructures.operations.Deposit deposit) {
    this.proof = deposit.getProof().streamUnboxed().collect(Collectors.toList());
    this.data = new DepositData(deposit.getData());
  }

  @JsonCreator
  public Deposit(
      @JsonProperty("proof") final List<Bytes32> proof,
      @JsonProperty("data") final DepositData data) {
    this.proof = proof;
    this.data = data;
  }

  public List<Bytes32> getProof() {
    return proof;
  }

  public void setProof(List<Bytes32> proof) {
    this.proof = proof;
  }

  public DepositData getData() {
    return data;
  }

  public void setData(DepositData data) {
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
    if (!(o instanceof Deposit)) {
      return false;
    }
    Deposit deposit = (Deposit) o;
    return Objects.equals(proof, deposit.proof) && Objects.equals(data, deposit.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(proof, data);
  }
}
