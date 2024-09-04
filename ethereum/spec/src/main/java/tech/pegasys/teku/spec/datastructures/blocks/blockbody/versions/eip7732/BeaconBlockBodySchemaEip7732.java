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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;

public interface BeaconBlockBodySchemaEip7732<T extends BeaconBlockBodyEip7732>
    extends BeaconBlockBodySchemaElectra<T> {

  static BeaconBlockBodySchemaEip7732<?> required(final BeaconBlockBodySchema<?> schema) {
    checkArgument(
        schema instanceof BeaconBlockBodySchemaEip7732,
        "Expected a BeaconBlockBodySchemaEip7732 but was %s",
        schema.getClass());
    return (BeaconBlockBodySchemaEip7732<?>) schema;
  }

  SignedExecutionPayloadHeaderSchema getSignedExecutionPayloadHeaderSchema();

  SszListSchema<PayloadAttestation, ?> getPayloadAttestationsSchema();

  long getBlobKzgCommitmentsRootGeneralizedIndex();

  @Override
  default Optional<BeaconBlockBodySchemaEip7732<?>> toVersionEip7732() {
    return Optional.of(this);
  }
}
