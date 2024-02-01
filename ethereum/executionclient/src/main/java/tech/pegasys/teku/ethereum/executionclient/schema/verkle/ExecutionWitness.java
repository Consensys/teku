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

package tech.pegasys.teku.ethereum.executionclient.schema.verkle;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tech.pegasys.teku.spec.datastructures.execution.verkle.ExecutionWitnessSchema;
import tech.pegasys.teku.spec.datastructures.execution.verkle.StemStateDiffSchema;

public class ExecutionWitness {

  @JsonProperty("stateDiff")
  private final List<StemStateDiff> stateDiff;

  @JsonProperty("verkleProof")
  private final VerkleProof verkleProof;

  public ExecutionWitness(
      @JsonProperty("stateDiff") final List<StemStateDiff> stateDiff,
      @JsonProperty("verkleProof") final VerkleProof verkleProof) {
    this.stateDiff = stateDiff;
    this.verkleProof = verkleProof;
  }

  public ExecutionWitness(
      final tech.pegasys.teku.spec.datastructures.execution.verkle.ExecutionWitness
          executionWitness) {
    this.stateDiff = executionWitness.getStateDiffs().stream().map(StemStateDiff::new).toList();
    this.verkleProof = new VerkleProof(executionWitness.getVerkleProof());
  }

  public tech.pegasys.teku.spec.datastructures.execution.verkle.ExecutionWitness
      asInternalExecutionWitness(final ExecutionWitnessSchema schema) {
    return schema.create(
        stateDiff.stream()
            .map(
                external ->
                    external.asInternalStemStateDiff(
                        (StemStateDiffSchema) schema.getStateDiffSchema().getElementSchema()))
            .toList(),
        verkleProof.asInternalVerkleProof(schema.getVerkleProofSchema()));
  }
}
