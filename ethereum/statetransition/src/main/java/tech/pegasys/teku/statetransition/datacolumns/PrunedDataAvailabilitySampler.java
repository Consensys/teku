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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.BlockPrunedTrackerFactory;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public interface PrunedDataAvailabilitySampler
    extends DataAvailabilitySampler, BlockPrunedTrackerFactory {
  PrunedDataAvailabilitySampler NOOP =
      new PrunedDataAvailabilitySampler() {
        @Override
        public void onNewBlock(
            final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {}

        @Override
        public void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {}

        @Override
        public void enableBlockImportOnCompletion(final SignedBeaconBlock block) {}

        @Override
        public SafeFuture<List<UInt64>> checkDataAvailability(
            final UInt64 slot, final Bytes32 blockRoot) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public void flush() {}

        @Override
        public SamplingEligibilityStatus checkSamplingEligibility(final BeaconBlock block) {
          return SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
        }

        @Override
        public void onNewValidatedDataColumnSidecar(
            final DataColumnSlotAndIdentifier columnId, final RemoteOrigin origin) {}
      };
}
