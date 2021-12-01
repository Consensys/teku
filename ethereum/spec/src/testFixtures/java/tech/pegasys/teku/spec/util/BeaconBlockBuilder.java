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

package tech.pegasys.teku.spec.util;

import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public class BeaconBlockBuilder {

  private final SpecVersion spec;
  private final DataStructureUtil dataStructureUtil;

  private SyncAggregate syncAggregate;
  private ExecutionPayload executionPayload;

  public BeaconBlockBuilder(final SpecVersion spec, final DataStructureUtil dataStructureUtil) {
    this.spec = spec;
    this.dataStructureUtil = dataStructureUtil;
    this.syncAggregate = dataStructureUtil.randomSyncAggregate();
  }

  public BeaconBlockBuilder syncAggregate(final SyncAggregate syncAggregate) {
    this.syncAggregate = syncAggregate;
    return this;
  }

  public BeaconBlockBuilder executionPayload(final ExecutionPayload executionPayload) {
    this.executionPayload = executionPayload;
    return this;
  }

  public BeaconBlock build() {
    final BeaconBlockBodySchema<?> bodySchema =
        spec.getSchemaDefinitions().getBeaconBlockBodySchema();

    if (syncAggregate == null) {
      syncAggregate = dataStructureUtil.randomSyncAggregate();
    }
    if (executionPayload == null) {
      executionPayload =
          spec.getSchemaDefinitions()
              .toVersionMerge()
              .map(definitions -> definitions.getExecutionPayloadSchema().getDefault())
              .orElse(null);
    }
    final BeaconBlockBody blockBody =
        bodySchema.createBlockBody(
            builder ->
                builder
                    .randaoReveal(dataStructureUtil.randomSignature())
                    .eth1Data(dataStructureUtil.randomEth1Data())
                    .graffiti(dataStructureUtil.randomBytes32())
                    .attestations(bodySchema.getAttestationsSchema().getDefault())
                    .proposerSlashings(bodySchema.getProposerSlashingsSchema().getDefault())
                    .attesterSlashings(bodySchema.getAttesterSlashingsSchema().getDefault())
                    .deposits(bodySchema.getDepositsSchema().getDefault())
                    .voluntaryExits(bodySchema.getVoluntaryExitsSchema().getDefault())
                    .syncAggregate(() -> syncAggregate)
                    .executionPayload(() -> executionPayload));
    return spec.getSchemaDefinitions()
        .getBeaconBlockSchema()
        .create(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            blockBody);
  }
}
