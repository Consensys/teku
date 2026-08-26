/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigHeze;

public class InclusionListByCommitteeRequestMessage
    extends Container3<InclusionListByCommitteeRequestMessage, SszUInt64, SszBytes32, SszBitvector>
    implements RpcRequest {

  public InclusionListByCommitteeRequestMessage(
      final UInt64 slot,
      final Bytes32 dependentRoot,
      final SszBitvector committeeIndices,
      final SpecConfigHeze specConfigHeze) {
    super(
        new InclusionListByCommitteeRequestMessageSchema(specConfigHeze),
        SszUInt64.of(slot),
        SszBytes32.of(dependentRoot),
        committeeIndices);
  }

  InclusionListByCommitteeRequestMessage(
      final InclusionListByCommitteeRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public Bytes32 getDependentRoot() {
    return getField1().get();
  }

  public SszBitvector getCommitteeIndices() {
    return getField2();
  }

  @Override
  public int getMaximumResponseChunks() {
    return getCommitteeIndices().getAllSetBits().size();
  }
}
