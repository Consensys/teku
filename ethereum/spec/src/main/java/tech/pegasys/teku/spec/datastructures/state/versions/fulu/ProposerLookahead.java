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

package tech.pegasys.teku.spec.datastructures.state.versions.fulu;

import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container1;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigFulu;

public class ProposerLookahead extends Container1<ProposerLookahead, SszUInt64Vector> {

  public static class ProposerLookaheadSchema
      extends ContainerSchema1<ProposerLookahead, SszUInt64Vector> {
    public ProposerLookaheadSchema(final SpecConfigFulu specConfig) {
      super(
          "ProposerLookahead",
          namedSchema(
              "validator_indices",
                  SszUInt64VectorSchema.create(
                  (long) (specConfig.getMinSeedLookahead() + 1) * specConfig.getSlotsPerEpoch())));
    }

    @Override
    public ProposerLookahead createFromBackingNode(final TreeNode node) {
      return new ProposerLookahead(this, node);
    }

    public ProposerLookahead create(final SszUInt64Vector validatorIndices) {
      return new ProposerLookahead(this, validatorIndices);
    }

    public SszUInt64VectorSchema<?> getPorposerLookaheadSchema() {
      return (SszUInt64VectorSchema<?>) getFieldSchema0();
    }
  }

  private ProposerLookahead(
      final ProposerLookahead.ProposerLookaheadSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private ProposerLookahead(
      final ProposerLookahead.ProposerLookaheadSchema type,
      final SszUInt64Vector validatorIndices) {
    super(type, validatorIndices);
  }

  public SszUInt64Vector getValidatorIndices() {
    return getField0();
  }
}
