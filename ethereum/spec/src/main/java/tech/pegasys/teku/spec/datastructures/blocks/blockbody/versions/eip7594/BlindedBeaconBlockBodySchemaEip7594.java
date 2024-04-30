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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDeneb;

public interface BlindedBeaconBlockBodySchemaEip7594<T extends BlindedBeaconBlockBodyEip7594>
    extends BlindedBeaconBlockBodySchemaDeneb<T> {

  static BlindedBeaconBlockBodySchemaEip7594<?> required(final BeaconBlockBodySchema<?> schema) {
    checkArgument(
        schema instanceof BlindedBeaconBlockBodySchemaEip7594,
        "Expected a BlindedBeaconBlockBodySchemaEip7594 but was %s",
        schema.getClass());
    return (BlindedBeaconBlockBodySchemaEip7594<?>) schema;
  }
}
