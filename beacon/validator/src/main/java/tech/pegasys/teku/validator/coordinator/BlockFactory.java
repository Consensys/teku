/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface BlockFactory {

  SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      BeaconState blockSlotState,
      UInt64 proposalSlot,
      BLSSignature randaoReveal,
      Optional<Bytes32> optionalGraffiti,
      Optional<UInt64> requestedBuilderBoostFactor,
      BlockProductionPerformance blockProductionPerformance);

  SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBlockIfBlinded(
      SignedBeaconBlock maybeBlindedBlock, BlockPublishingPerformance blockPublishingPerformance);

  List<BlobSidecar> createBlobSidecars(SignedBlockContainer blockContainer);

  List<DataColumnSidecar> createDataColumnSidecars(SignedBlockContainer blockContainer);
}
