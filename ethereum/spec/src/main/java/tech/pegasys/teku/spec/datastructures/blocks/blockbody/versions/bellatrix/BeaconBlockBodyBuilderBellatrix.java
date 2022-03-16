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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyBuilderAltair;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

class BeaconBlockBodyBuilderBellatrix extends BeaconBlockBodyBuilderAltair {
  private BeaconBlockBodySchemaBellatrixImpl schema;
  private BlindedBeaconBlockBodySchemaBellatrixImpl blindedSchema;
  protected boolean blinded = false;
  protected ExecutionPayload executionPayload;
  protected ExecutionPayloadHeader executionPayloadHeader;

  public BeaconBlockBodyBuilderBellatrix schema(final BeaconBlockBodySchemaBellatrixImpl schema) {
    this.schema = schema;
    this.blinded = false;
    return this;
  }

  public BeaconBlockBodyBuilderBellatrix blindedSchema(
      final BlindedBeaconBlockBodySchemaBellatrixImpl schema) {
    this.blindedSchema = schema;
    this.blinded = true;
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayload(
      Supplier<ExecutionPayload> executionPayloadSupplier) {
    if (!blinded) {
      this.executionPayload = executionPayloadSupplier.get();
    }
    return this;
  }

  @Override
  public BeaconBlockBodyBuilder executionPayloadHeader(
      Supplier<ExecutionPayloadHeader> executionPayloadHeaderSupplier) {
    if (blinded) {
      this.executionPayloadHeader = executionPayloadHeaderSupplier.get();
    }
    return this;
  }

  @Override
  protected void validateSchema() {
    if (blinded) {
      checkNotNull(blindedSchema, "schema must be specified");
    } else {
      checkNotNull(schema, "schema must be specified");
    }
  }

  @Override
  protected void validate() {
    super.validate();
    if (blinded) {
      checkNotNull(executionPayloadHeader, "executionPayloadHeader must be specified");
    } else {
      checkNotNull(executionPayload, "executionPayload must be specified");
    }
  }

  @Override
  public BeaconBlockBody build() {
    validate();
    if (blinded) {
      return new BlindedBeaconBlockBodyBellatrixImpl(
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
          executionPayloadHeader);
    }
    return new BeaconBlockBodyBellatrixImpl(
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
        executionPayload);
  }
}
