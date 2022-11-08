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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBuilderBellatrix;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

class BeaconBlockBodyBuilderCapella extends BeaconBlockBodyBuilderBellatrix {

  private BeaconBlockBodySchemaCapellaImpl schema;
  private BlindedBeaconBlockBodySchemaCapellaImpl blindedSchema;
  private SszList<SignedBlsToExecutionChange> blsToExecutionChanges;

  public BeaconBlockBodyBuilderCapella schema(final BeaconBlockBodySchemaCapellaImpl schema) {
    this.schema = schema;
    this.blinded = Optional.of(false);
    return this;
  }

  public BeaconBlockBodyBuilderCapella blindedSchema(
      final BlindedBeaconBlockBodySchemaCapellaImpl blindedSchema) {
    this.blindedSchema = blindedSchema;
    this.blinded = Optional.of(true);
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder blsToExecutionChanges(
      final Supplier<SszList<SignedBlsToExecutionChange>> blsToExecutionChanges) {
    this.blsToExecutionChanges = blsToExecutionChanges.get();
    return this;
  }

  @Override
  protected void validateSchema() {
    checkState(schema != null || blindedSchema != null, "schema or blindedSchema must be set");
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(blsToExecutionChanges, "blsToExecutionChanges must be specified");
  }

  @Override
  public Boolean isBlinded() {
    return blinded.orElseThrow(
        () ->
            new IllegalStateException(
                "schema or blindedSchema must be set before interacting with the builder"));
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
                  header,
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
                payload,
                blsToExecutionChanges));
  }
}
