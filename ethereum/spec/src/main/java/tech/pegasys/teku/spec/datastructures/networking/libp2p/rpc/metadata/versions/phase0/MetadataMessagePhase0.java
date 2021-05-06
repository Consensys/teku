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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.containers.Container2;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.tree.TreeNode;

/** https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata */
public class MetadataMessagePhase0
    extends Container2<MetadataMessagePhase0, SszUInt64, SszBitvector> implements MetadataMessage {

  MetadataMessagePhase0(MetadataMessageSchemaPhase0 type, TreeNode backingNode) {
    super(type, backingNode);
  }

  MetadataMessagePhase0(MetadataMessageSchemaPhase0 type) {
    super(type);
  }

  MetadataMessagePhase0(
      MetadataMessageSchemaPhase0 schema, UInt64 seqNumber, SszBitvector attnets) {
    super(schema, SszUInt64.of(seqNumber), attnets);
  }

  @Override
  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  @Override
  public SszBitvector getAttnets() {
    return getField1();
  }
}
