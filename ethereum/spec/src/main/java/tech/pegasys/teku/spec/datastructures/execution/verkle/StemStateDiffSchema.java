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
import tech.pegasys.teku.infrastructure.bytes.Bytes31;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class StemStateDiffSchema
    extends ContainerSchema2<StemStateDiff, SszByteVector, SszList<SuffixStateDiff>> {

  static final SszFieldName FIELD_SUFFIX_DIFFS = () -> "suffix_diffs";

  public StemStateDiffSchema(final int verkleWidth) {
    super(
        "StemStateDiff",
        namedSchema("stem", SszByteVectorSchema.create(Bytes31.SIZE)),
        namedSchema(
            FIELD_SUFFIX_DIFFS, SszListSchema.create(SuffixStateDiffSchema.INSTANCE, verkleWidth)));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SuffixStateDiff, SszList<SuffixStateDiff>> getSuffixDiffsSchema() {
    return (SszListSchema<SuffixStateDiff, SszList<SuffixStateDiff>>)
        getChildSchema(getFieldIndex(FIELD_SUFFIX_DIFFS));
  }

  public StemStateDiff create(final Bytes31 stem, final List<SuffixStateDiff> stateDiffs) {
    return new StemStateDiff(this, stem, stateDiffs);
  }

  @Override
  public StemStateDiff createFromBackingNode(TreeNode node) {
    return new StemStateDiff(this, node);
  }
}
