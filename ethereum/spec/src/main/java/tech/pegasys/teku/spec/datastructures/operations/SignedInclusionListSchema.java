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

package tech.pegasys.teku.spec.datastructures.operations;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INCLUSION_LIST_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SignedInclusionListSchema
    extends ContainerSchema2<SignedInclusionList, InclusionList, SszSignature> {

  public SignedInclusionListSchema(
      final String containerName, final SchemaRegistry schemaRegistry) {
    super(
        containerName,
        namedSchema("message", schemaRegistry.get(INCLUSION_LIST_SCHEMA)),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public InclusionListSchema getInclusionListSchema() {
    return (InclusionListSchema) getFieldSchema0();
  }

  @Override
  public SignedInclusionList createFromBackingNode(final TreeNode node) {
    return new SignedInclusionList(this, node);
  }

  public SignedInclusionList create(final InclusionList message, final BLSSignature signature) {
    return new SignedInclusionList(this, message, signature);
  }
}
