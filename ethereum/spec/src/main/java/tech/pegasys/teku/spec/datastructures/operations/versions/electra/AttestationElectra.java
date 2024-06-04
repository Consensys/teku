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

package tech.pegasys.teku.spec.datastructures.operations.versions.electra;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class AttestationElectra
    extends Container4<AttestationElectra, SszBitlist, AttestationData, SszBitvector, SszSignature>
    implements Attestation {

  public AttestationElectra(final AttestationElectraSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttestationElectra(
      final AttestationElectraSchema schema,
      final SszBitlist aggregationBits,
      final AttestationData data,
      final SszBitvector committeeBits,
      final BLSSignature signature) {
    super(schema, aggregationBits, data, committeeBits, new SszSignature(signature));
  }

  @Override
  public AttestationElectraSchema getSchema() {
    return (AttestationElectraSchema) super.getSchema();
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
  public Optional<SszBitvector> getCommitteeBits() {
    return Optional.of(getField2());
  }

  @Override
  public BLSSignature getAggregateSignature() {
    return getField3().getSignature();
  }

  @Override
  public Optional<List<UInt64>> getCommitteeIndices() {
    return Optional.of(
        getCommitteeBitsRequired().getAllSetBits().intStream().mapToObj(UInt64::valueOf).toList());
  }

  @Override
  public UInt64 getFirstCommitteeIndex() {
    return UInt64.valueOf(getCommitteeBitsRequired().streamAllSetBits().findFirst().orElseThrow());
  }

  @Override
  public boolean requiresCommitteeBits() {
    return true;
  }
}
