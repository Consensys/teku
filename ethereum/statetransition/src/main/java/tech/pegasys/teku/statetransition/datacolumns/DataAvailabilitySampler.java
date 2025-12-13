/*
 * Copyright Consensys Software Inc., 2024
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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public interface DataAvailabilitySampler {

  enum SamplingEligibilityStatus {
    NOT_REQUIRED_OLD_EPOCH,
    NOT_REQUIRED_NO_BLOBS,
    NOT_REQUIRED_BEFORE_FULU,
    REQUIRED
  }

  DataAvailabilitySampler NOOP =
      new DataAvailabilitySampler() {
        @Override
        public SafeFuture<List<UInt64>> checkDataAvailability(UInt64 slot, Bytes32 blockRoot) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public void flush() {}

        @Override
        public SamplingEligibilityStatus checkSamplingEligibility(BeaconBlock block) {
          return SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
        }

        @Override
        public void onNewValidatedDataColumnSidecar(
            final DataColumnSlotAndIdentifier columnId, RemoteOrigin origin) {}
      };

  /**
   * Schedules availability check. To initiate all scheduled availability checks immediately call
   * the {@link #flush()} method
   */
  SafeFuture<List<UInt64>> checkDataAvailability(UInt64 slot, Bytes32 blockRoot);

  /**
   * Immediately initiates sampling. When doing batch sampling it would be more effective to invoke
   * flush after submitting all requests
   */
  void flush();

  SamplingEligibilityStatus checkSamplingEligibility(BeaconBlock block);

  void onNewValidatedDataColumnSidecar(DataColumnSlotAndIdentifier columnId, RemoteOrigin origin);

  default void onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    onNewValidatedDataColumnSidecar(
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar), remoteOrigin);
  }
}
