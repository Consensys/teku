/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.operations.versions.phase0;

import com.google.common.collect.Sets;
import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class AttestationPhase0
    extends Container3<AttestationPhase0, SszBitlist, AttestationData, SszSignature>
    implements Attestation {

  AttestationPhase0(final AttestationPhase0Schema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  AttestationPhase0(
      final AttestationPhase0Schema schema,
      final SszBitlist aggregationBits,
      final AttestationData data,
      final BLSSignature signature) {
    super(schema, aggregationBits, data, new SszSignature(signature));
  }

  @Override
  public AttestationPhase0Schema getSchema() {
    return (AttestationPhase0Schema) super.getSchema();
  }

  @Override
  public UInt64 getEarliestSlotForForkChoiceProcessing(final Spec spec) {
    return getData().getEarliestSlotForForkChoice(spec);
  }

  @Override
  public Collection<Bytes32> getDependentBlockRoots() {
    return Sets.newHashSet(getData().getTarget().getRoot(), getData().getBeaconBlockRoot());
  }

  @Override
  public SszBitlist getAggregationBits() {
    return getField0();
  }

  @Override
  public AttestationData getData() {
    return getField1();
  }

  @Override
  public UInt64 getFirstCommitteeIndex() {
    return getField1().getIndex();
  }

  @Override
  public BLSSignature getAggregateSignature() {
    return getField2().getSignature();
  }

  @Override
  public boolean requiresCommitteeBits() {
    return false;
  }
}
