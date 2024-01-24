/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.json.types.wrappers;

import static tech.pegasys.teku.ethereum.json.types.wrappers.ProposerDuty.PROPOSER_DUTY_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class ProposerDuties {
  public static final DeserializableTypeDefinition<ProposerDuties> PROPOSER_DUTIES_TYPE =
      DeserializableTypeDefinition.object(
              ProposerDuties.class, ProposerDuties.ProposerDutiesBuilder.class)
          .name("GetProposerDutiesResponse")
          .initializer(ProposerDuties.ProposerDutiesBuilder::new)
          .finisher(ProposerDuties.ProposerDutiesBuilder::build)
          .withField(
              "dependent_root",
              BYTES32_TYPE,
              ProposerDuties::getDependentRoot,
              ProposerDuties.ProposerDutiesBuilder::dependentRoot)
          .withField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              ProposerDuties::isExecutionOptimistic,
              ProposerDuties.ProposerDutiesBuilder::executionOptimistic)
          .withField(
              "data",
              listOf(PROPOSER_DUTY_TYPE),
              ProposerDuties::getDuties,
              ProposerDuties.ProposerDutiesBuilder::duties)
          .build();

  private final Bytes32 dependentRoot;
  private final boolean executionOptimistic;
  private final List<ProposerDuty> duties;

  public ProposerDuties(
      final Bytes32 dependentRoot,
      final List<ProposerDuty> duties,
      final boolean executionOptimistic) {
    this.dependentRoot = dependentRoot;
    this.executionOptimistic = executionOptimistic;
    this.duties = duties;
  }

  public Bytes32 getDependentRoot() {
    return dependentRoot;
  }

  public boolean isExecutionOptimistic() {
    return executionOptimistic;
  }

  public List<ProposerDuty> getDuties() {
    return duties;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProposerDuties that = (ProposerDuties) o;
    return Objects.equals(dependentRoot, that.dependentRoot) && Objects.equals(duties, that.duties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dependentRoot, duties);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dependentRoot", dependentRoot)
        .add("duties", duties)
        .toString();
  }

  public static final class ProposerDutiesBuilder {
    private Bytes32 dependentRoot;
    private boolean executionOptimistic;
    private List<ProposerDuty> duties;

    public ProposerDutiesBuilder dependentRoot(Bytes32 dependentRoot) {
      this.dependentRoot = dependentRoot;
      return this;
    }

    public ProposerDutiesBuilder executionOptimistic(boolean executionOptimistic) {
      this.executionOptimistic = executionOptimistic;
      return this;
    }

    public ProposerDutiesBuilder duties(List<ProposerDuty> duties) {
      this.duties = duties;
      return this;
    }

    public ProposerDuties build() {
      return new ProposerDuties(dependentRoot, duties, executionOptimistic);
    }
  }
}
