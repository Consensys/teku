/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.ssz.containers.Container3;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class ContributionAndProof
    extends Container3<ContributionAndProof, SszUInt64, SyncCommitteeContribution, SszSignature> {

  ContributionAndProof(final ContributionAndProofSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public UInt64 getAggregatorIndex() {
    return getField0().get();
  }

  public SyncCommitteeContribution getContribution() {
    return getField1();
  }

  public BLSSignature getSignature() {
    return getField2().getSignature();
  }
}
