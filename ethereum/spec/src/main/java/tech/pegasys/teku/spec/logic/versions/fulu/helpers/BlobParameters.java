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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;

public record BlobParameters(UInt64 epoch, int maxBlobsPerBlock) {
  static BlobParameters fromBlobSchedule(final BlobScheduleEntry blobScheduleEntry) {
    return new BlobParameters(blobScheduleEntry.epoch(), blobScheduleEntry.maxBlobsPerBlock());
  }

  // used in computeForkDigestInternal
  static Bytes32 hash(final BlobParameters blobParameters) {
    final Bytes epochBytes = uint64ToBytes(blobParameters.epoch());
    final Bytes maxBlobsPerBlockBytes =
        uint64ToBytes(UInt64.valueOf(blobParameters.maxBlobsPerBlock()));
    final Bytes concat = Bytes.wrap(epochBytes, maxBlobsPerBlockBytes);
    return Hash.sha256(concat);
  }
}
