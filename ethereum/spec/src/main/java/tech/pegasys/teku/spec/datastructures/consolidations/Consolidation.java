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

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Consolidation extends Container3<Consolidation, SszUInt64, SszUInt64, SszUInt64> {
  public static final ConsolidationSchema SSZ_SCHEMA = new ConsolidationSchema();

  public static class ConsolidationSchema
      extends ContainerSchema3<Consolidation, SszUInt64, SszUInt64, SszUInt64> {
    public ConsolidationSchema() {
      super(
          "Consolidation",
          namedSchema("source_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("target_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    public Consolidation create(
        final SszUInt64 sourceIndex, final SszUInt64 targetIndex, final SszUInt64 epoch) {
      return new Consolidation(this, sourceIndex, targetIndex, epoch);
    }

    public SszUInt64 getSourceIndexSchema() {
      return (SszUInt64) getFieldSchema0();
    }

    public SszUInt64 getTargetIndexSchema() {
      return (SszUInt64) getFieldSchema1();
    }

    public SszUInt64 getEpochSchema() {
      return (SszUInt64) getFieldSchema2();
    }

    @Override
    public Consolidation createFromBackingNode(final TreeNode node) {
      return new Consolidation(this, node);
    }
  }

  protected Consolidation(
      final ContainerSchema3<Consolidation, SszUInt64, SszUInt64, SszUInt64> schema) {
    super(schema);
  }

  private Consolidation(final Consolidation.ConsolidationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Consolidation(
      final ConsolidationSchema consolidationSchema,
      final SszUInt64 sourceIndex,
      final SszUInt64 targetIndex,
      final SszUInt64 epoch) {
    super(consolidationSchema, sourceIndex, targetIndex, epoch);
  }

  public int getSourceIndex() {
    return ((SszUInt64) get(0)).get().intValue();
  }

  public int getTargetIndex() {
    return ((SszUInt64) get(1)).get().intValue();
  }

  public UInt64 getEpoch() {
    return ((SszUInt64) get(2)).get();
  }
}
