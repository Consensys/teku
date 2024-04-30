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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyBuilderDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7594.ExecutionPayloadEip7594Impl;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7594.ExecutionPayloadHeaderEip7594Impl;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderEip7594 extends BeaconBlockBodyBuilderDeneb {

  public BeaconBlockBodyBuilderEip7594(
      final BeaconBlockBodySchema<? extends BeaconBlockBodyEip7594> schema,
      final BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyEip7594> blindedSchema) {
    super(schema, blindedSchema);
  }

  @Override
  protected void validate() {
    super.validate();
  }

  @Override
  public BeaconBlockBody build() {
    validate();
    if (isBlinded()) {
      final BlindedBeaconBlockBodySchemaEip7594Impl schema =
          getAndValidateSchema(true, BlindedBeaconBlockBodySchemaEip7594Impl.class);
      return new BlindedBeaconBlockBodyEip7594Impl(
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
          (ExecutionPayloadHeaderEip7594Impl)
              executionPayloadHeader.toVersionEip7594().orElseThrow(),
          getBlsToExecutionChanges(),
          getBlobKzgCommitments());
    }

    final BeaconBlockBodySchemaEip7594Impl schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaEip7594Impl.class);
    return new BeaconBlockBodyEip7594Impl(
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
        (ExecutionPayloadEip7594Impl) executionPayload.toVersionEip7594().orElseThrow(),
        getBlsToExecutionChanges(),
        getBlobKzgCommitments());
  }
}
