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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class IpaProof
    extends Container3<IpaProof, SszVector<SszBytes32>, SszVector<SszBytes32>, SszBytes32> {

  IpaProof(final IpaProofSchema ipaProofSchema, final TreeNode backingTreeNode) {
    super(ipaProofSchema, backingTreeNode);
  }

  public IpaProof(
      final IpaProofSchema schema,
      final List<SszBytes32> cl,
      final List<SszBytes32> cr,
      final SszBytes32 finalEvaluation) {
    super(
        schema,
        schema.getClSchema().createFromElements(cl),
        schema.getClSchema().createFromElements(cr),
        finalEvaluation);
  }

  public IpaProof(
      final IpaProofSchema schema,
      final List<Bytes32> cl,
      final List<Bytes32> cr,
      final Bytes32 finalEvaluation) {
    this(
        schema,
        cl.stream().map(SszBytes32::of).toList(),
        cr.stream().map(SszBytes32::of).toList(),
        SszBytes32.of(finalEvaluation));
  }

  public List<Bytes32> getCl() {
    return getField0().stream().map(SszBytes32::get).toList();
  }

  public List<Bytes32> getCr() {
    return getField1().stream().map(SszBytes32::get).toList();
  }

  public Bytes32 getFinalEvaluation() {
    return getField2().get();
  }
}
