/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync;

import com.google.common.primitives.UnsignedLong;

public class SyncStatus {
  private final UnsignedLong startingSlot;
  private final UnsignedLong currentSlot;
  private final UnsignedLong highestSlot;

  public SyncStatus(
      final UnsignedLong startingSlot,
      final UnsignedLong currentSlot,
      final UnsignedLong highest_slot) {
    this.startingSlot = startingSlot;
    this.currentSlot = currentSlot;
    this.highestSlot = highest_slot;
  }

  public UnsignedLong getStartingSlot() {
    return startingSlot;
  }

  public UnsignedLong getCurrentSlot() {
    return currentSlot;
  }

  public UnsignedLong getHighestSlot() {
    return highestSlot;
  }
}
