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

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7594.ExecutionPayloadEip7594;

public interface BeaconBlockBodyEip7594 extends BeaconBlockBodyDeneb {
  static BeaconBlockBodyEip7594 required(final BeaconBlockBody body) {
    return body.toVersionEip7594()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected EIP7594 block body but got " + body.getClass().getSimpleName()));
  }

  @Override
  BeaconBlockBodySchemaEip7594<?> getSchema();

  @Override
  ExecutionPayloadEip7594 getExecutionPayload();

  @Override
  default Optional<BeaconBlockBodyEip7594> toVersionEip7594() {
    return Optional.of(this);
  }
}
