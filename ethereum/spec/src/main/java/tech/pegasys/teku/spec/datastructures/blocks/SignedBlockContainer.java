/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;

/**
 * Interface used to represent both {@link SignedBeaconBlock} and {@link SignedBlockContents} and
 * their blinded variants: <a
 * href="https://github.com/ethereum/beacon-APIs/tree/master/types/deneb">beacon-APIs/types/deneb</a>
 */
public interface SignedBlockContainer extends SszData, SszContainer {

  SignedBeaconBlock getSignedBlock();

  default UInt64 getSlot() {
    return getSignedBlock().getSlot();
  }

  default Bytes32 getRoot() {
    return getSignedBlock().getRoot();
  }

  default Optional<List<SignedBlobSidecar>> getSignedBlobSidecars() {
    return Optional.empty();
  }

  default Optional<SignedBlindedBlockContainer> toBlinded() {
    return Optional.empty();
  }

  default boolean isBlinded() {
    return toBlinded().isPresent();
  }
}
