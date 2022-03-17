/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.block;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockImportPerformance {
  private final TimeProvider timeProvider;
  private UInt64 blockArrivalTimeStamp;
  private UInt64 importCompletedTimeStamp;
  private UInt64 arrivalDelay;
  private UInt64 timeWarningLimitTimeStamp;
  private UInt64 processingTime;

  public BlockImportPerformance(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  public void arrival(final RecentChainData recentChainData, final UInt64 slot) {
    blockArrivalTimeStamp = timeProvider.getTimeInMillis();
    final UInt64 timeAtSlotStartTimeStamp =
        secondsToMillis(recentChainData.computeTimeAtSlot(slot));

    arrivalDelay = blockArrivalTimeStamp.minusMinZero(timeAtSlotStartTimeStamp);

    timeWarningLimitTimeStamp =
        timeAtSlotStartTimeStamp.plus(
            secondsToMillis(recentChainData.getSpec().getSecondsPerSlot(slot)).dividedBy(3));
  }

  public void processed() {
    importCompletedTimeStamp = timeProvider.getTimeInMillis();
    processingTime = timeProvider.getTimeInMillis().minus(blockArrivalTimeStamp);
  }

  public UInt64 getBlockArrivalTimeStamp() {
    return blockArrivalTimeStamp;
  }

  public UInt64 getArrivalDelay() {
    return arrivalDelay;
  }

  public boolean isSlotTimeWarningPassed() {
    return importCompletedTimeStamp.isGreaterThan(timeWarningLimitTimeStamp);
  }

  public UInt64 getProcessingTime() {
    return processingTime;
  }
}
