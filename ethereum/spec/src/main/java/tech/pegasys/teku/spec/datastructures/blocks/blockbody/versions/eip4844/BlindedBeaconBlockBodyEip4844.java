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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BlindedBeaconBlockBodyEip4844 extends BlindedBeaconBlockBodyCapella {
  static BlindedBeaconBlockBodyEip4844 required(final BeaconBlockBody body) {
    return body.toBlindedVersionEip4844()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a EIP-4844 blinded block body but got: "
                        + body.getClass().getSimpleName()));
  }

  @Override
  default Optional<BlindedBeaconBlockBodyEip4844> toBlindedVersionEip4844() {
    return Optional.of(this);
  }

  SszList<SszKZGCommitment> getBlobKzgCommitments();

  @Override
  BlindedBeaconBlockBodySchemaEip4844<?> getSchema();
}
