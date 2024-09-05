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

package tech.pegasys.teku.spec.datastructures.operations.versions.phase0;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class IndexedAttestationPhase0
    extends Container3<IndexedAttestationPhase0, SszUInt64List, AttestationData, SszSignature>
    implements IndexedAttestation {

  IndexedAttestationPhase0(final IndexedAttestationPhase0Schema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  IndexedAttestationPhase0(
      final IndexedAttestationPhase0Schema schema,
      final SszUInt64List attestingIndices,
      final AttestationData data,
      final BLSSignature signature) {
    super(schema, attestingIndices, data, new SszSignature(signature));
  }

  @Override
  public IndexedAttestationPhase0Schema getSchema() {
    return (IndexedAttestationPhase0Schema) super.getSchema();
  }

  @Override
  public SszUInt64List getAttestingIndices() {
    return getField0();
  }

  @Override
  public AttestationData getData() {
    return getField1();
  }

  @Override
  public BLSSignature getSignature() {
    return getField2().getSignature();
  }
}
