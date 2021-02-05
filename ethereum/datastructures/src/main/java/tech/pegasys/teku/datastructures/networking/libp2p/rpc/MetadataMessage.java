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
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas.SszBitVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

/** https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata */
public class MetadataMessage extends Container2<MetadataMessage, SszUInt64, SszVector<SszBit>>
    implements RpcRequest {

  public static class MetadataMessageType
      extends ContainerType2<MetadataMessage, SszUInt64, SszVector<SszBit>> {

    public MetadataMessageType() {
      super(
          "MetadataMessage",
          namedSchema("seqNumber", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("attnets", new SszBitVectorSchema(Constants.ATTESTATION_SUBNET_COUNT)));
    }

    @Override
    public MetadataMessage createFromBackingNode(TreeNode node) {
      return new MetadataMessage(this, node);
    }
  }

  public static final MetadataMessageType TYPE = new MetadataMessageType();
  public static final MetadataMessage DEFAULT = new MetadataMessage();

  private Bitvector attnetsCache;

  private MetadataMessage(MetadataMessageType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  private MetadataMessage() {
    super(TYPE);
  }

  public MetadataMessage(UInt64 seqNumber, Bitvector attnets) {
    super(TYPE, new SszUInt64(seqNumber), SszUtils.toSszBitVector(attnets));
    checkArgument(attnets.getSize() == Constants.ATTESTATION_SUBNET_COUNT, "Invalid vector size");
    this.attnetsCache = attnets;
  }

  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  public Bitvector getAttnets() {
    if (attnetsCache == null) {
      attnetsCache = SszUtils.getBitvector(getField1());
    }
    return attnetsCache;
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}
