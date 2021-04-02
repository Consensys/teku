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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import tech.pegasys.teku.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SyncCommitteeSigningDataSchema
    extends ContainerSchema2<SyncCommitteeSigningData, SszUInt64, SszUInt64> {

  public static final SyncCommitteeSigningDataSchema INSTANCE =
      new SyncCommitteeSigningDataSchema();

  private SyncCommitteeSigningDataSchema() {
    super(
        "SyncCommitteeSigningData",
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("subcommittee_index", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  @Override
  public SyncCommitteeSigningData createFromBackingNode(final TreeNode node) {
    return new SyncCommitteeSigningData(this, node);
  }
}
