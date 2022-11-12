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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadEip4844;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BeaconBlockBodyEip4844 extends BeaconBlockBodyCapella {
  static BeaconBlockBodyEip4844 required(final BeaconBlockBody body) {
    return body.toVersionEip4844()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected EIP-4844 block body but got " + body.getClass().getSimpleName()));
  }

  SszList<SszKZGCommitment> getBlobKzgCommitments();

  @Override
  BeaconBlockBodySchemaEip4844<?> getSchema();

  @Override
  ExecutionPayloadEip4844 getExecutionPayload();

  @Override
  default Optional<BeaconBlockBodyEip4844> toVersionEip4844() {
    return Optional.of(this);
  }
}
