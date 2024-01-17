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
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class StemStateDiff
    extends Container2<StemStateDiff, SszByteVector, SszList<SuffixStateDiff>> {

  StemStateDiff(final StemStateDiffSchema stemStateDiffSchema, final TreeNode backingTreeNode) {
    super(stemStateDiffSchema, backingTreeNode);
  }

  public StemStateDiff(
      final StemStateDiffSchema schema,
      final SszByteVector stem,
      final List<SuffixStateDiff> stateDiffs) {
    super(schema, stem, schema.getSuffixDiffsSchema().createFromElements(stateDiffs));
  }

  public StemStateDiff(
      final StemStateDiffSchema schema,
      final Bytes31 stem,
      final List<SuffixStateDiff> stateDiffs) {
    this(schema, SszByteVector.fromBytes(stem.getWrappedBytes()), stateDiffs);
  }

  public Bytes31 getStem() {
    return new Bytes31(getField0().getBytes());
  }

  public List<SuffixStateDiff> getStateDiffs() {
    return getField1().asList();
  }
}
