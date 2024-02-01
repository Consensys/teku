/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.execution.verkle;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes31;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class VerkleProofSchema
    extends ContainerSchema5<
        VerkleProof,
        SszList<SszByteVector>,
        SszByteList,
        SszList<SszBytes32>,
        SszBytes32,
        IpaProof> {

  static final SszFieldName FIELD_OTHER_STEMS = () -> "other_stems";
  static final SszFieldName FIELD_DEPTH_EXTENSION_PRESENT = () -> "depth_extension_present";
  static final SszFieldName FIELD_COMMITMENTS_BY_PATH = () -> "commitments_by_path";
  static final SszFieldName FIELD_IPA_PROOF = () -> "ipa_proof";

  public VerkleProofSchema(
      final IpaProofSchema ipaProofSchema, final int maxStems, final int maxCommitmentsPerStem) {
    super(
        "VerkleProof",
        namedSchema(
            FIELD_OTHER_STEMS,
            SszListSchema.create(SszByteVectorSchema.create(Bytes31.SIZE), maxStems)),
        namedSchema(FIELD_DEPTH_EXTENSION_PRESENT, SszByteListSchema.create(maxStems)),
        namedSchema(
            FIELD_COMMITMENTS_BY_PATH,
            SszListSchema.create(
                SszPrimitiveSchemas.BYTES32_SCHEMA, (long) maxStems * maxCommitmentsPerStem)),
        namedSchema("d", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(FIELD_IPA_PROOF, ipaProofSchema));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszByteVector, SszList<SszByteVector>> getOtherStemsSchema() {
    return (SszListSchema<SszByteVector, SszList<SszByteVector>>)
        getChildSchema(getFieldIndex(FIELD_OTHER_STEMS));
  }

  public SszByteListSchema<?> getDepthExtensionPresentSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex(FIELD_DEPTH_EXTENSION_PRESENT));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszBytes32, SszList<SszBytes32>> getCommitmentsByPathSchema() {
    return (SszListSchema<SszBytes32, SszList<SszBytes32>>)
        getChildSchema(getFieldIndex(FIELD_COMMITMENTS_BY_PATH));
  }

  public IpaProofSchema getIpaProofSchema() {
    return (IpaProofSchema) getChildSchema(getFieldIndex(FIELD_IPA_PROOF));
  }

  public VerkleProof create(
      final List<Bytes31> otherStems,
      final Bytes depthExtensionPresent,
      final List<Bytes32> commitmentsByPath,
      final Bytes32 d,
      final IpaProof ipaProof) {
    return new VerkleProof(this, otherStems, depthExtensionPresent, commitmentsByPath, d, ipaProof);
  }

  @Override
  public VerkleProof createFromBackingNode(TreeNode node) {
    return new VerkleProof(this, node);
  }
}
