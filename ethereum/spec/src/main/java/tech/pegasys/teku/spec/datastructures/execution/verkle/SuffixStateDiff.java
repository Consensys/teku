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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SuffixStateDiff
    extends Container3<SuffixStateDiff, SszByte, SszOptional<SszBytes32>, SszOptional<SszBytes32>> {

  SuffixStateDiff(
      final SuffixStateDiffSchema suffixStateDiffSchema, final TreeNode backingTreeNode) {
    super(suffixStateDiffSchema, backingTreeNode);
  }

  public SuffixStateDiff(
      final SuffixStateDiffSchema schema,
      final SszByte suffix,
      final Optional<SszBytes32> currentValue,
      final Optional<SszBytes32> newValue) {
    super(
        schema,
        suffix,
        schema.getCurrentValueSchema().createFromValue(currentValue),
        schema.getCurrentValueSchema().createFromValue(newValue));
  }

  public SuffixStateDiff(
      final SuffixStateDiffSchema schema,
      final Byte suffix,
      final Optional<Bytes32> currentValue,
      final Optional<Bytes32> newValue) {
    this(
        schema, SszByte.of(suffix), currentValue.map(SszBytes32::of), newValue.map(SszBytes32::of));
  }

  public Byte getSuffix() {
    return getField0().get();
  }

  public Optional<Bytes32> getCurrentValue() {
    return getField1().getValue().map(AbstractSszPrimitive::get);
  }

  public Optional<Bytes32> getNewValue() {
    return getField2().getValue().map(AbstractSszPrimitive::get);
  }
}
