/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;

public class MetadataMessageSchemaAltair
    extends ContainerSchema3<MetadataMessageAltair, SszUInt64, SszBitvector, SszBitvector>
    implements MetadataMessageSchema<MetadataMessageAltair> {
  public MetadataMessageSchemaAltair() {
    super(
        "MetadataMessage",
        namedSchema("seq_number", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("attnets", SszBitvectorSchema.create(Constants.ATTESTATION_SUBNET_COUNT)),
        namedSchema(
            "syncnets", SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT)));
  }

  @Override
  public MetadataMessageAltair create(
      final UInt64 seqNumber, final Iterable<Integer> attnets, final Iterable<Integer> syncnets) {
    return new MetadataMessageAltair(
        this, seqNumber, getAttnestSchema().ofBits(attnets), getSyncnetsSchema().ofBits(syncnets));
  }

  @Override
  public MetadataMessageAltair createDefault() {
    return new MetadataMessageAltair(this);
  }

  @Override
  public MetadataMessageAltair createFromBackingNode(final TreeNode node) {
    return new MetadataMessageAltair(this, node);
  }

  private SszBitvectorSchema<SszBitvector> getAttnestSchema() {
    return (SszBitvectorSchema<SszBitvector>) getFieldSchema1();
  }

  private SszBitvectorSchema<SszBitvector> getSyncnetsSchema() {
    return (SszBitvectorSchema<SszBitvector>) getFieldSchema2();
  }
}
