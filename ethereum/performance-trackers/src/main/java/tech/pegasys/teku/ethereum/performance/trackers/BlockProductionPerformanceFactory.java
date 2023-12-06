/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.performance.trackers;

import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockProductionPerformanceFactory {
  private final TimeProvider timeProvider;
  private final boolean enabled;
  private final int lateEventThreshold;

  public BlockProductionPerformanceFactory(
      final TimeProvider timeProvider, final boolean enabled, final int lateEventThreshold) {
    this.timeProvider = timeProvider;
    this.enabled = enabled;
    this.lateEventThreshold = lateEventThreshold;
  }

  public BlockProductionPerformance create(final UInt64 slot) {
    if (enabled) {
      return new BlockProductionPerformanceImpl(timeProvider, slot, lateEventThreshold);
    } else {
      return BlockProductionPerformance.NOOP;
    }
  }
}
