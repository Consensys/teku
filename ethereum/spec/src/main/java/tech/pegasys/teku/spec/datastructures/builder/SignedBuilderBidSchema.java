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

package tech.pegasys.teku.spec.datastructures.builder;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedBuilderBidSchema
    extends ContainerSchema2<SignedBuilderBid, BuilderBid, SszSignature>
    implements SszContainerSchema<SignedBuilderBid> {

  public SignedBuilderBidSchema(
      final String schemaName, final BuilderBidSchema<?> builderBidSchema) {
    super(
        schemaName,
        namedSchema("message", SszSchema.as(BuilderBid.class, builderBidSchema)),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SignedBuilderBid create(final BuilderBid message, final BLSSignature signature) {
    return new SignedBuilderBid(this, message, signature);
  }

  @Override
  public SignedBuilderBid createFromBackingNode(TreeNode node) {
    return new SignedBuilderBid(this, node);
  }
}
