/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

/** https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata */
public class MetadataMessage
    extends Container2<MetadataMessage, UInt64View, VectorViewRead<BitView>>
    implements RpcRequest, SimpleOffsetSerializable, SSZContainer {

  public static final MetadataMessage DEFAULT = new MetadataMessage();

  static class MetadataMessageType
      extends ContainerType2<MetadataMessage, UInt64View, VectorViewRead<BitView>> {

    public MetadataMessageType() {
      super(
          BasicViewTypes.UINT64_TYPE,
          new VectorViewType<>(BasicViewTypes.BIT_TYPE, Constants.ATTESTATION_SUBNET_COUNT));
    }

    @Override
    public MetadataMessage createFromBackingNode(TreeNode node) {
      return new MetadataMessage(this, node);
    }
  }

  @SszTypeDescriptor public static final MetadataMessageType TYPE = new MetadataMessageType();

  private Bitvector attnetsCache;

  private MetadataMessage(
      ContainerType2<MetadataMessage, UInt64View, VectorViewRead<BitView>> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  private MetadataMessage() {
    super(TYPE);
  }

  public MetadataMessage(UInt64 seqNumber, Bitvector attnets) {
    super(TYPE, new UInt64View(seqNumber), ViewUtils.createBitvectorView(attnets));
    checkArgument(attnets.getSize() == Constants.ATTESTATION_SUBNET_COUNT, "Invalid vector size");
    this.attnetsCache = attnets;
  }

  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  public Bitvector getAttnets() {
    if (attnetsCache == null) {
      attnetsCache = ViewUtils.getBitvector(getField1());
    }
    return attnetsCache;
  }

  @Override
  public String toString() {
    return "MetadataMessage{" + "seqNumber=" + getSeqNumber() + ", attnets=" + getAttnets() + '}';
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}
