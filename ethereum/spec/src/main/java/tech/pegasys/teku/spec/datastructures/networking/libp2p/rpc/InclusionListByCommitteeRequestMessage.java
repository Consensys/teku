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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;

public class InclusionListByCommitteeRequestMessage
    extends Container2<InclusionListByCommitteeRequestMessage, SszUInt64, SszBitvector>
    implements RpcRequest {

  private final Optional<Integer> maxRequestInclusionList;

  public InclusionListByCommitteeRequestMessage(
      final UInt64 slot,
      final SszBitvector committeeIndices,
      final SpecConfigEip7805 specConfigEip7805) {
    super(
        new InclusionListByCommitteeRequestMessageSchema(specConfigEip7805),
        SszUInt64.of(slot),
        committeeIndices);
    this.maxRequestInclusionList = Optional.of(specConfigEip7805.getMaxRequestInclusionList());
  }

  InclusionListByCommitteeRequestMessage(
      final InclusionListByCommitteeRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
    this.maxRequestInclusionList = Optional.empty();
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public SszBitvector getCommitteeIndices() {
    return getField1();
  }

  @Override
  public int getMaximumResponseChunks() {
    return maxRequestInclusionList.orElseThrow(
        () -> new IllegalStateException("Unexpected method usage"));
  }
}
