/*
 * Copyright Consensys Software Inc., 2024
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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CELL_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMNS_BY_ROOT_IDENTIFIER_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SIDECARS_BY_RANGE_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DATA_COLUMN_SIDECAR_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_AND_BLOBS_CELL_BUNDLE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.MATRIX_ENTRY_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.CellSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntrySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.fulu.BeaconBlockBodyBuilderFulu;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.fulu.ExecutionPayloadAndBlobsCellBundleSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsFulu extends SchemaDefinitionsElectra {

  private final CellSchema cellSchema;
  private final DataColumnSchema dataColumnSchema;
  private final DataColumnSidecarSchema dataColumnSidecarSchema;
  private final DataColumnsByRootIdentifierSchema dataColumnsByRootIdentifierSchema;
  private final MatrixEntrySchema matrixEntrySchema;
  private final ExecutionPayloadAndBlobsCellBundleSchema executionPayloadAndBlobsCellBundleSchema;

  private final DataColumnSidecarsByRootRequestMessageSchema
      dataColumnSidecarsByRootRequestMessageSchema;
  private final DataColumnSidecarsByRangeRequestMessage
          .DataColumnSidecarsByRangeRequestMessageSchema
      dataColumnSidecarsByRangeRequestMessageSchema;

  public SchemaDefinitionsFulu(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.cellSchema = schemaRegistry.get(CELL_SCHEMA);
    this.dataColumnSchema = schemaRegistry.get(DATA_COLUMN_SCHEMA);
    this.dataColumnSidecarSchema = schemaRegistry.get(DATA_COLUMN_SIDECAR_SCHEMA);
    this.dataColumnsByRootIdentifierSchema =
        schemaRegistry.get(DATA_COLUMNS_BY_ROOT_IDENTIFIER_SCHEMA);
    this.matrixEntrySchema = schemaRegistry.get(MATRIX_ENTRY_SCHEMA);
    this.executionPayloadAndBlobsCellBundleSchema =
        schemaRegistry.get(EXECUTION_PAYLOAD_AND_BLOBS_CELL_BUNDLE_SCHEMA);
    this.dataColumnSidecarsByRootRequestMessageSchema =
        schemaRegistry.get(DATA_COLUMN_SIDECARS_BY_ROOT_REQUEST_MESSAGE_SCHEMA);
    this.dataColumnSidecarsByRangeRequestMessageSchema =
        schemaRegistry.get(DATA_COLUMN_SIDECARS_BY_RANGE_REQUEST_MESSAGE_SCHEMA);
  }

  public static SchemaDefinitionsFulu required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsFulu,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsFulu.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsFulu) schemaDefinitions;
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderFulu(
        getBeaconBlockBodySchema().toVersionElectra().orElseThrow(),
        getBlindedBeaconBlockBodySchema().toBlindedVersionElectra().orElseThrow());
  }

  public CellSchema getCellSchema() {
    return cellSchema;
  }

  public DataColumnSchema getDataColumnSchema() {
    return dataColumnSchema;
  }

  public DataColumnSidecarSchema getDataColumnSidecarSchema() {
    return dataColumnSidecarSchema;
  }

  public DataColumnsByRootIdentifierSchema getDataColumnsByRootIdentifierSchema() {
    return dataColumnsByRootIdentifierSchema;
  }

  public MatrixEntrySchema getMatrixEntrySchema() {
    return matrixEntrySchema;
  }

  public ExecutionPayloadAndBlobsCellBundleSchema getExecutionPayloadAndBlobsCellBundleSchema() {
    return executionPayloadAndBlobsCellBundleSchema;
  }

  @Override
  public BuilderPayloadSchema<?> getBuilderPayloadSchema() {
    return getExecutionPayloadAndBlobsCellBundleSchema();
  }

  public DataColumnSidecarsByRootRequestMessageSchema
      getDataColumnSidecarsByRootRequestMessageSchema() {
    return dataColumnSidecarsByRootRequestMessageSchema;
  }

  public DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema
      getDataColumnSidecarsByRangeRequestMessageSchema() {
    return dataColumnSidecarsByRangeRequestMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsFulu> toVersionFulu() {
    return Optional.of(this);
  }
}
