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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.eip7594;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class MetadataMessageEip7594
    extends Container4<MetadataMessageEip7594, SszUInt64, SszBitvector, SszBitvector, SszUInt64>
    implements MetadataMessage {

  MetadataMessageEip7594(final MetadataMessageSchemaEip7594 schema) {
    super(schema);
  }

  MetadataMessageEip7594(final MetadataMessageSchemaEip7594 schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  MetadataMessageEip7594(
      final MetadataMessageSchemaEip7594 schema,
      final UInt64 seqNumber,
      final SszBitvector attNets,
      final SszBitvector syncNets,
      final UInt64 custodySubnetCount) {
    super(schema, SszUInt64.of(seqNumber), attNets, syncNets, SszUInt64.of(custodySubnetCount));
  }

  @Override
  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  @Override
  public SszBitvector getAttnets() {
    return getField1();
  }

  public SszBitvector getSyncnets() {
    return getField2();
  }

  public UInt64 getCustodySubnetCount() {
    return getField3().get();
  }

  @Override
  public Optional<SszBitvector> getOptionalSyncnets() {
    return Optional.of(getSyncnets());
  }

  @Override
  public Optional<UInt64> getOptionalCustodySubnetCount() {
    return Optional.of(getCustodySubnetCount());
  }
}
