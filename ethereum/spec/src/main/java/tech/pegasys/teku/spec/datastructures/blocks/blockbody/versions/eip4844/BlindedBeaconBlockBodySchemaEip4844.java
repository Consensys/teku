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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BlindedBeaconBlockBodySchemaEip4844<T extends BlindedBeaconBlockBodyEip4844>
    extends BlindedBeaconBlockBodySchemaCapella<T> {

  static BlindedBeaconBlockBodySchemaEip4844<?> required(final BeaconBlockBodySchema<?> schema) {
    checkArgument(
        schema instanceof BlindedBeaconBlockBodySchemaEip4844,
        "Expected a BlindedBeaconBlockBodySchemaEip4844 but was %s",
        schema.getClass());
    return (BlindedBeaconBlockBodySchemaEip4844<?>) schema;
  }

  SszListSchema<SszKZGCommitment, ?> getBlobKzgCommitmentsSchema();

  default Optional<BlindedBeaconBlockBodySchemaEip4844<?>> toBlindedVersionEip4844() {
    return Optional.of(this);
  }
}
