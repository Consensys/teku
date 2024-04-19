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

package tech.pegasys.teku.spec.datastructures.consolidations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedConsolidation
    extends Container2<SignedConsolidation, Consolidation, SszSignature> {
  public static final SignedConsolidationSchema SSZ_SCHEMA = new SignedConsolidationSchema();

  protected SignedConsolidation(
      final ContainerSchema2<SignedConsolidation, Consolidation, SszSignature> schema,
      final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected SignedConsolidation(
      final ContainerSchema2<SignedConsolidation, Consolidation, SszSignature> schema,
      final Consolidation arg0,
      final SszSignature arg1) {
    super(schema, arg0, arg1);
  }

  public static class SignedConsolidationSchema
      extends ContainerSchema2<SignedConsolidation, Consolidation, SszSignature> {

    public SignedConsolidationSchema() {
      super(
          "SignedConsolidation",
          namedSchema("message", Consolidation.SSZ_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    @Override
    public SignedConsolidation createFromBackingNode(final TreeNode node) {
      return new SignedConsolidation(this, node);
    }

    public SignedConsolidation create(
        final Consolidation consolidation, final BLSSignature signature) {
      return new SignedConsolidation(this, consolidation, new SszSignature(signature));
    }
  }

  public Consolidation getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }
}
