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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.merge;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.util.config.Constants;

public class MetadataMessageSchemaMerge
    extends ContainerSchema2<MetadataMessageMerge, SszUInt64, SszBitvector>
    implements MetadataMessageSchema<MetadataMessageMerge> {

  public MetadataMessageSchemaMerge() {
    super(
        "MetadataMessage",
        namedSchema("seqNumber", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("attnets", SszBitvectorSchema.create(Constants.ATTESTATION_SUBNET_COUNT)));
  }

  @Override
  public MetadataMessageMerge createFromBackingNode(TreeNode node) {
    return new MetadataMessageMerge(this, node);
  }

  @Override
  public MetadataMessageMerge create(
      final UInt64 seqNumber, final Iterable<Integer> attnets, final Iterable<Integer> syncnets) {
    return new MetadataMessageMerge(this, seqNumber, getAttnestSchema().ofBits(attnets));
  }

  @Override
  public MetadataMessageMerge createDefault() {
    return new MetadataMessageMerge(this);
  }

  private SszBitvectorSchema<SszBitvector> getAttnestSchema() {
    return (SszBitvectorSchema<SszBitvector>) getFieldSchema1();
  }
}
