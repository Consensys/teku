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

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.InclusionListByCommitteeRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionListSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes;

public class SchemaDefinitionsEip7805 extends SchemaDefinitionsElectra {

  private final InclusionListSchema inclusionListSchema;
  private final SignedInclusionListSchema signedInclusionListSchema;
  private final InclusionListByCommitteeRequestMessageSchema
      inclusionListByCommitteeRequestMessageSchema;

  public SchemaDefinitionsEip7805(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.inclusionListSchema = schemaRegistry.get(SchemaTypes.INCLUSION_LIST_SCHEMA);
    this.signedInclusionListSchema = schemaRegistry.get(SchemaTypes.SIGNED_INCLUSION_LIST_SCHEMA);
    this.inclusionListByCommitteeRequestMessageSchema =
        schemaRegistry.get(SchemaTypes.INCLUSION_LIST_BY_COMMITTEE_INDICES_REQUEST_MESSAGE_SCHEMA);
  }

  public static SchemaDefinitionsEip7805 required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsEip7805,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsEip7805.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsEip7805) schemaDefinitions;
  }

  public InclusionListSchema getInclusionListSchema() {
    return inclusionListSchema;
  }

  public SignedInclusionListSchema getSignedInclusionListSchema() {
    return signedInclusionListSchema;
  }

  public InclusionListByCommitteeRequestMessageSchema
      getInclusionListByCommitteeRequestMessageSchema() {
    return inclusionListByCommitteeRequestMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsEip7805> toVersionEip7805() {
    return Optional.of(this);
  }
}
