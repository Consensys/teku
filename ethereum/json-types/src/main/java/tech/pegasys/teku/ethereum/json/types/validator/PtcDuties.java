/*
 * Copyright Consensys Software Inc., 2025
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

import static tech.pegasys.teku.ethereum.json.types.validator.PtcDuty.PTC_DUTY_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.DEPENDENT_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public record PtcDuties(boolean executionOptimistic, Bytes32 dependentRoot, List<PtcDuty> duties) {

  public static final DeserializableTypeDefinition<PtcDuties> PTC_DUTIES_TYPE_DEFINITION =
      DeserializableTypeDefinition.object(PtcDuties.class, PtcDuties.Builder.class)
          .name("GetPtcDutiesResponse")
          .initializer(PtcDuties.Builder::new)
          .finisher(PtcDuties.Builder::build)
          .withField(
              DEPENDENT_ROOT,
              BYTES32_TYPE,
              PtcDuties::dependentRoot,
              PtcDuties.Builder::dependentRoot)
          .withField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              PtcDuties::executionOptimistic,
              PtcDuties.Builder::executionOptimistic)
          .withField(
              "data",
              listOf(PTC_DUTY_TYPE_DEFINITION),
              PtcDuties::duties,
              PtcDuties.Builder::duties)
          .build();

  public static class Builder {

    private boolean executionOptimistic;
    private Bytes32 dependentRoot;
    private List<PtcDuty> duties;

    public Builder executionOptimistic(final boolean executionOptimistic) {
      this.executionOptimistic = executionOptimistic;
      return this;
    }

    public Builder dependentRoot(final Bytes32 dependentRoot) {
      this.dependentRoot = dependentRoot;
      return this;
    }

    public Builder duties(final List<PtcDuty> duties) {
      this.duties = duties;
      return this;
    }

    public PtcDuties build() {
      return new PtcDuties(executionOptimistic, dependentRoot, duties);
    }
  }
}
