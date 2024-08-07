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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyBuilderElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectraImpl;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderEip7732 extends BeaconBlockBodyBuilderElectra {

  public BeaconBlockBodyBuilderEip7732(
      final BeaconBlockBodySchema<? extends BeaconBlockBodyEip7732> schema) {
    super(schema, null);
  }

  @Override
  protected void validate() {
    super.validate();
  }

  @Override
  public BeaconBlockBody build() {
    validate();

    final BeaconBlockBodySchemaEip7732Impl schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaEip7732Impl.class);
    return new BeaconBlockBodyElectraImpl(
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
        (ExecutionPayloadElectraImpl) executionPayload.toVersionElectra().orElseThrow(),
        getBlsToExecutionChanges(),
        getBlobKzgCommitments());
  }
}
