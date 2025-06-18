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

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigFulu;

/** A helper class to navigate the blob schedule in an efficient manner */
public class BlobSchedule {

  private final NavigableMap<UInt64, BlobParameters> epochToBlobParameters = new TreeMap<>();

  public BlobSchedule(final SpecConfigFulu specConfig) {
    specConfig
        .getBlobSchedule()
        .forEach(
            blobScheduleEntry ->
                epochToBlobParameters.put(
                    blobScheduleEntry.epoch(),
                    BlobParameters.fromBlobScheduleEntry(blobScheduleEntry)));
  }

  public Optional<BlobParameters> getBlobParameters(final UInt64 epoch) {
    return Optional.ofNullable(epochToBlobParameters.floorEntry(epoch)).map(Map.Entry::getValue);
  }

  @SuppressWarnings("unused")
  public Optional<BlobParameters> getNextBlobParameters(final UInt64 epoch) {
    return Optional.ofNullable(epochToBlobParameters.ceilingEntry(epoch.plus(1)))
        .map(Map.Entry::getValue);
  }

  public Optional<Integer> getHighestMaxBlobsPerBlock() {
    return Optional.ofNullable(epochToBlobParameters.lastEntry())
        .map(entry -> entry.getValue().maxBlobsPerBlock());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlobSchedule that = (BlobSchedule) o;
    return Objects.equals(epochToBlobParameters, that.epochToBlobParameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(epochToBlobParameters);
  }
}
