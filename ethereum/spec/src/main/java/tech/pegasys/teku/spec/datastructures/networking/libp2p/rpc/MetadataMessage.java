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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.util.config.Constants;

/** https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata */
public class MetadataMessage extends Container2<MetadataMessage, SszUInt64, SszBitvector>
    implements RpcRequest {

  public static class MetadataMessageSchema
      extends ContainerSchema2<MetadataMessage, SszUInt64, SszBitvector> {

    public MetadataMessageSchema() {
      super(
          "MetadataMessage",
          namedSchema("seqNumber", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("attnets", SszBitvectorSchema.create(Constants.ATTESTATION_SUBNET_COUNT)));
    }

    @Override
    public MetadataMessage createFromBackingNode(TreeNode node) {
      return new MetadataMessage(this, node);
    }
  }

  public static final MetadataMessageSchema SSZ_SCHEMA = new MetadataMessageSchema();
  public static final MetadataMessage DEFAULT = new MetadataMessage();

  private MetadataMessage(MetadataMessageSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  private MetadataMessage() {
    super(SSZ_SCHEMA);
  }

  public MetadataMessage(UInt64 seqNumber, SszBitvector attnets) {
    super(SSZ_SCHEMA, SszUInt64.of(seqNumber), attnets);
  }

  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  public SszBitvector getAttnets() {
    return getField1();
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}
