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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes31Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes31Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes31;
import tech.pegasys.teku.spec.datastructures.execution.verkle.VerkleProofSchema;

public class VerkleProof {

  @JsonSerialize(contentUsing = Bytes31Serializer.class)
  @JsonDeserialize(contentUsing = Bytes31Deserializer.class)
  @JsonProperty("otherStems")
  private final List<Bytes31> otherStems;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonProperty("depthExtensionPresent")
  private final Bytes depthExtensionPresent;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = Bytes32Deserializer.class)
  @JsonProperty("commitmentsByPath")
  private final List<Bytes32> commitmentsByPath;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  @JsonProperty("d")
  private final Bytes32 d;

  @JsonProperty("ipaProof")
  private final IpaProof ipaProof;

  public VerkleProof(
      @JsonProperty("otherStems") final List<Bytes31> otherStems,
      @JsonProperty("depthExtensionPresent") final Bytes depthExtensionPresent,
      @JsonProperty("commitmentsByPath") final List<Bytes32> commitmentsByPath,
      @JsonProperty("d") final Bytes32 d,
      @JsonProperty("ipaProof") final IpaProof ipaProof) {
    this.otherStems = otherStems;
    this.depthExtensionPresent = depthExtensionPresent;
    this.commitmentsByPath = commitmentsByPath;
    this.d = d;
    this.ipaProof = ipaProof;
  }

  public VerkleProof(
      final tech.pegasys.teku.spec.datastructures.execution.verkle.VerkleProof verkleProof) {
    this.otherStems = verkleProof.getOtherStems();
    this.depthExtensionPresent = verkleProof.getDepthExtensionPresent();
    this.commitmentsByPath = verkleProof.getCommitmentsByPath();
    this.d = verkleProof.getD();
    this.ipaProof = new IpaProof(verkleProof.getIpaProof());
  }

  public tech.pegasys.teku.spec.datastructures.execution.verkle.VerkleProof asInternalVerkleProof(
      final VerkleProofSchema schema) {
    return schema.create(
        otherStems,
        depthExtensionPresent,
        commitmentsByPath,
        d,
        ipaProof.asInternalIpaProof(schema.getIpaProofSchema()));
  }
}
