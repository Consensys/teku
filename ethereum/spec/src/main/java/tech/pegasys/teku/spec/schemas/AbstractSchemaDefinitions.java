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

package tech.pegasys.teku.spec.schemas;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes;

public abstract class AbstractSchemaDefinitions implements SchemaDefinitions {
  protected SchemaRegistry schemaRegistry;

  final SszBitvectorSchema<SszBitvector> attnetsENRFieldSchema;
  final SszBitvectorSchema<SszBitvector> syncnetsENRFieldSchema =
      SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT);
  private final HistoricalBatchSchema historicalBatchSchema;
  private final BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema
      beaconBlocksByRootRequestMessageSchema;

  public AbstractSchemaDefinitions(final SchemaRegistry schemaRegistry) {
    this.schemaRegistry = schemaRegistry;
    this.historicalBatchSchema =
        new HistoricalBatchSchema(schemaRegistry.getSpecConfig().getSlotsPerHistoricalRoot());

    this.beaconBlocksByRootRequestMessageSchema =
        new BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema(
            schemaRegistry.getSpecConfig());
    this.attnetsENRFieldSchema = schemaRegistry.get(SchemaTypes.ATTNETS_ENR_FIELD_SCHEMA);
  }

  abstract long getMaxValidatorPerAttestation(SpecConfig specConfig);

  @Override
  public SchemaRegistry getSchemaRegistry() {
    return schemaRegistry;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getAttnetsENRFieldSchema() {
    return attnetsENRFieldSchema;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getSyncnetsENRFieldSchema() {
    return syncnetsENRFieldSchema;
  }

  @Override
  public HistoricalBatchSchema getHistoricalBatchSchema() {
    return historicalBatchSchema;
  }

  @Override
  public BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema
      getBeaconBlocksByRootRequestMessageSchema() {
    return beaconBlocksByRootRequestMessageSchema;
  }
}
