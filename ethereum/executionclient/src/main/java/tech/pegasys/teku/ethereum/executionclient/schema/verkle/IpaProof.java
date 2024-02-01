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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.spec.datastructures.execution.verkle.IpaProofSchema;

public class IpaProof {

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = Bytes32Deserializer.class)
  @JsonProperty("cl")
  private final List<Bytes32> cl;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = Bytes32Deserializer.class)
  @JsonProperty("cr")
  private final List<Bytes32> cr;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  @JsonProperty("finalEvaluation")
  private final Bytes32 finalEvaluation;

  public IpaProof(
      @JsonProperty("cl") final List<Bytes32> cl,
      @JsonProperty("cr") final List<Bytes32> cr,
      @JsonProperty("finalEvaluation") final Bytes32 finalEvaluation) {
    this.cl = cl;
    this.cr = cr;
    this.finalEvaluation = finalEvaluation;
  }

  public IpaProof(final tech.pegasys.teku.spec.datastructures.execution.verkle.IpaProof ipaProof) {
    this.cl = ipaProof.getCl();
    this.cr = ipaProof.getCr();
    this.finalEvaluation = ipaProof.getFinalEvaluation();
  }

  public tech.pegasys.teku.spec.datastructures.execution.verkle.IpaProof asInternalIpaProof(
      final IpaProofSchema schema) {
    return schema.create(cl, cr, finalEvaluation);
  }
}
