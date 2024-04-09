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

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockProductionAndPublishingPerformanceFactory {
  private final TimeProvider timeProvider;
  private final boolean enabled;
  private final int lateProductionEventThreshold;
  private final int latePublishingEventThreshold;
  private final Function<UInt64, UInt64> slotTimeCalculator;

  public BlockProductionAndPublishingPerformanceFactory(
      final TimeProvider timeProvider,
      final Function<UInt64, UInt64> slotTimeCalculator,
      final boolean enabled,
      final int lateProductionEventThreshold,
      final int latePublishingEventThreshold) {
    this.timeProvider = timeProvider;
    this.slotTimeCalculator = slotTimeCalculator;
    this.enabled = enabled;
    this.lateProductionEventThreshold = lateProductionEventThreshold;
    this.latePublishingEventThreshold = latePublishingEventThreshold;
  }

  public BlockProductionPerformance createForProduction(final UInt64 slot) {
    if (enabled) {
      return new BlockProductionPerformanceImpl(
          timeProvider, slot, slotTimeCalculator.apply(slot), lateProductionEventThreshold);
    } else {
      return BlockProductionPerformance.NOOP;
    }
  }

  public BlockPublishingPerformance createForPublishing(final UInt64 slot) {
    if (enabled) {
      return new BlockPublishingPerformanceImpl(
          timeProvider, slot, slotTimeCalculator.apply(slot), latePublishingEventThreshold);
    } else {
      return BlockPublishingPerformance.NOOP;
    }
  }
}
