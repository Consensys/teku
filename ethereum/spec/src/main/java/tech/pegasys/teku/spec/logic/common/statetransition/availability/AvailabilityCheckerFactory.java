/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.common.statetransition.availability;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

@FunctionalInterface
public interface AvailabilityCheckerFactory<T> {
  AvailabilityCheckerFactory<BlobSidecar> NOOP_BLOB_SIDECAR =
      block -> AvailabilityChecker.NOOP_BLOB_SIDECAR;
  AvailabilityCheckerFactory<UInt64> NOOP_DATACOLUMN_SIDECAR =
      block -> AvailabilityChecker.NOOP_DATACOLUMN_SIDECAR;

  AvailabilityChecker<T> createAvailabilityChecker(SignedBeaconBlock block);
}
