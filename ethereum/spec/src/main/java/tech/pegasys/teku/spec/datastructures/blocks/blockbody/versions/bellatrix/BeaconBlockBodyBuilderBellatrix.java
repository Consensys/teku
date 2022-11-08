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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyBuilderAltair;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderBellatrix extends BeaconBlockBodyBuilderAltair {
  private BeaconBlockBodySchemaBellatrixImpl schema;
  private BlindedBeaconBlockBodySchemaBellatrixImpl blindedSchema;
  protected Optional<Boolean> blinded = Optional.empty();
  protected SafeFuture<ExecutionPayload> executionPayload;
  protected SafeFuture<ExecutionPayloadHeader> executionPayloadHeader;

  public BeaconBlockBodyBuilderBellatrix schema(final BeaconBlockBodySchemaBellatrixImpl schema) {
    this.schema = schema;
    this.blinded = Optional.of(false);
    return this;
  }

  public BeaconBlockBodyBuilderBellatrix blindedSchema(
      final BlindedBeaconBlockBodySchemaBellatrixImpl schema) {
    this.blindedSchema = schema;
    this.blinded = Optional.of(true);
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayload(
      Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
    if (!isBlinded()) {
      this.executionPayload = executionPayloadSupplier.get();
    }
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayloadHeader(
      Supplier<SafeFuture<ExecutionPayloadHeader>> executionPayloadHeaderSupplier) {
    if (isBlinded()) {
      this.executionPayloadHeader = executionPayloadHeaderSupplier.get();
    }
    return this;
  }

  @Override
  protected void validateSchema() {
    checkState(schema != null || blindedSchema != null, "schema or blindedSchema must be set");
  }

  @Override
  protected void validate() {
    super.validate();
    if (isBlinded()) {
      checkNotNull(executionPayloadHeader, "executionPayloadHeader must be specified");
    } else {
      checkNotNull(executionPayload, "executionPayload must be specified");
    }
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
              new BlindedBeaconBlockBodyBellatrixImpl(
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
                  header));
    }
    return executionPayload.thenApply(
        payload ->
            new BeaconBlockBodyBellatrixImpl(
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
                payload));
  }
}
