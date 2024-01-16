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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ExecutionWitness
    extends Container2<ExecutionWitness, SszList<StemStateDiff>, VerkleProof> {

  ExecutionWitness(
      final ExecutionWitnessSchema executionWitnessSchema, final TreeNode backingTreeNode) {
    super(executionWitnessSchema, backingTreeNode);
  }

  public ExecutionWitness(
      final ExecutionWitnessSchema schema,
      final List<StemStateDiff> stateDiffList,
      final VerkleProof verkleProof) {
    super(schema, schema.getStateDiffSchema().createFromElements(stateDiffList), verkleProof);
  }

  public List<StemStateDiff> getStateDiffs() {
    return getField0().asList();
  }

  public VerkleProof getVerkleProof() {
    return getField1();
  }
}
