/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.ethereum.json.types.validator;

import static tech.pegasys.teku.ethereum.json.types.validator.PayloadAttesterDutyBuilder.PAYLOAD_ATTESTER_DUTY_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class PayloadAttesterDutiesBuilder {
  public static final DeserializableTypeDefinition<PayloadAttesterDuties>
      PAYLOAD_ATTESTER_DUTIES_RESPONSE_TYPE =
          DeserializableTypeDefinition.object(
                  PayloadAttesterDuties.class, PayloadAttesterDutiesBuilder.class)
              .name("GetPayloadAttesterDutiesResponse")
              .initializer(PayloadAttesterDutiesBuilder::new)
              .finisher(PayloadAttesterDutiesBuilder::build)
              .withField(
                  "dependent_root",
                  BYTES32_TYPE,
                  PayloadAttesterDuties::dependentRoot,
                  PayloadAttesterDutiesBuilder::dependentRoot)
              .withField(
                  EXECUTION_OPTIMISTIC,
                  BOOLEAN_TYPE,
                  PayloadAttesterDuties::executionOptimistic,
                  PayloadAttesterDutiesBuilder::executionOptimistic)
              .withField(
                  "data",
                  DeserializableTypeDefinition.listOf(PAYLOAD_ATTESTER_DUTY_TYPE),
                  PayloadAttesterDuties::duties,
                  PayloadAttesterDutiesBuilder::duties)
              .build();

  private boolean executionOptimistic;
  private Bytes32 dependentRoot;
  private List<PayloadAttesterDuty> duties;

  public PayloadAttesterDutiesBuilder executionOptimistic(final boolean executionOptimistic) {
    this.executionOptimistic = executionOptimistic;
    return this;
  }

  public PayloadAttesterDutiesBuilder dependentRoot(final Bytes32 dependentRoot) {
    this.dependentRoot = dependentRoot;
    return this;
  }

  public PayloadAttesterDutiesBuilder duties(final List<PayloadAttesterDuty> duties) {
    this.duties = duties;
    return this;
  }

  public PayloadAttesterDuties build() {
    return new PayloadAttesterDuties(executionOptimistic, dependentRoot, duties);
  }
}
