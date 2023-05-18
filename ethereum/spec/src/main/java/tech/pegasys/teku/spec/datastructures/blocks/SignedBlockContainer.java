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
import java.util.function.Predicate;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;

public interface SignedBlockContainer extends BlockContainer {

  Predicate<SignedBlockContainer> IS_SIGNED_BLOCK_CONTENTS =
      blockContainer -> blockContainer instanceof SignedBlockContents;

  Predicate<SignedBlockContainer> IS_SIGNED_BEACON_BLOCK =
      blockContainer -> blockContainer instanceof SignedBeaconBlock;

  SignedBeaconBlock getSignedBlock();

  default Optional<List<SignedBlobSidecar>> getSignedBlobSidecars() {
    return Optional.empty();
  }
}
