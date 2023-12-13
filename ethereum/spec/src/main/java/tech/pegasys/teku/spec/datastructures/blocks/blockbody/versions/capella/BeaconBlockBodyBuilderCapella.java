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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBuilderBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapellaImpl;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderCapellaImpl;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderCapella extends BeaconBlockBodyBuilderBellatrix {

  private final BeaconBlockBodySchemaCapellaImpl schema;
  private final BlindedBeaconBlockBodySchemaCapellaImpl blindedSchema;
  private SszList<SignedBlsToExecutionChange> blsToExecutionChanges;

  public BeaconBlockBodyBuilderCapella() {
    this.schema = null;
    this.blindedSchema = null;
  }

  public BeaconBlockBodyBuilderCapella(
      final BeaconBlockBodySchemaCapellaImpl schema,
      final BlindedBeaconBlockBodySchemaCapellaImpl blindedSchema) {
    this.schema = schema;
    this.blindedSchema = blindedSchema;
  }

  protected SszList<SignedBlsToExecutionChange> getBlsToExecutionChanges() {
    return blsToExecutionChanges;
  }

  @Override
  public Boolean supportsBlsToExecutionChanges() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder blsToExecutionChanges(
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges) {
    this.blsToExecutionChanges = blsToExecutionChanges;
    return this;
  }

  @Override
  protected void validateSchema() {
    if (isBlinded()) {
      checkNotNull(blindedSchema, "blindedSchema must be set when blinded body has been requested");
    } else {
      checkNotNull(schema, "schema must be set if when non blinded body has been requested");
    }
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(blsToExecutionChanges, "blsToExecutionChanges must be specified");
  }

  @Override
  public SafeFuture<BeaconBlockBody> build() {
    validate();
    if (isBlinded()) {
      return executionPayloadHeader.thenApply(
          header ->
              new BlindedBeaconBlockBodyCapellaImpl(
                  blindedSchema,
                  new SszSignature(randaoReveal),
                  eth1Data,
                  SszBytes32.of(graffiti),
                  proposerSlashings,
                  attesterSlashings,
                  attestations,
                  deposits,
                  voluntaryExits,
                  syncAggregate,
                  (ExecutionPayloadHeaderCapellaImpl) header.toVersionCapella().orElseThrow(),
                  blsToExecutionChanges));
    }
    return executionPayload.thenApply(
        payload ->
            new BeaconBlockBodyCapellaImpl(
                schema,
                new SszSignature(randaoReveal),
                eth1Data,
                SszBytes32.of(graffiti),
                proposerSlashings,
                attesterSlashings,
                attestations,
                deposits,
                voluntaryExits,
                syncAggregate,
                (ExecutionPayloadCapellaImpl) payload.toVersionCapella().orElseThrow(),
                blsToExecutionChanges));
  }
}
