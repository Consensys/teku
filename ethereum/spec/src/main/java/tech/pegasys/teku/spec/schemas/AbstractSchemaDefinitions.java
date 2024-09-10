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

import static tech.pegasys.teku.spec.schemas.SchemaTypes.ATTNETS_ENR_FIELD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.HISTORICAL_BATCH_SCHEMA;
import static tech.pegasys.teku.spec.schemas.SchemaTypes.SYNCNETS_ENR_FIELD_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;

public abstract class AbstractSchemaDefinitions implements SchemaDefinitions {

  protected SchemaRegistry schemaRegistry;

  public AbstractSchemaDefinitions(final SchemaRegistry schemaRegistry) {
    this.schemaRegistry = schemaRegistry;
  }

  @Override
  public SchemaRegistry getSchemaRegistry() {
    return schemaRegistry;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getAttnetsENRFieldSchema() {
    return schemaRegistry.get(ATTNETS_ENR_FIELD_SCHEMA);
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getSyncnetsENRFieldSchema() {
    return schemaRegistry.get(SYNCNETS_ENR_FIELD_SCHEMA);
  }

  @Override
  public HistoricalBatchSchema getHistoricalBatchSchema() {
    return schemaRegistry.get(HISTORICAL_BATCH_SCHEMA);
  }

  @Override
  public BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema
      getBeaconBlocksByRootRequestMessageSchema() {
    return schemaRegistry.get(BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA);
  }
}
