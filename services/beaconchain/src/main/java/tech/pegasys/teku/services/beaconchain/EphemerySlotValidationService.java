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

package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.storage.client.RecentChainData;

public class EphemerySlotValidationService extends Service implements SlotEventsChannel {

  private final RecentChainData recentChainData;

  public EphemerySlotValidationService(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 currentSlot =
        recentChainData
            .getCurrentSlot()
            .orElseThrow(() -> new IllegalStateException("Current slot not available"));
    final UInt64 maxSlot = currentSlot.max(recentChainData.getCurrentSlot().orElse(ZERO));

    if (slot.minus(currentSlot).compareTo(maxSlot) > 0) {
      throw new IllegalStateException(
          String.format(
              "Slot %s is too far ahead of current slot %s (max allowed: %s)",
              slot, currentSlot, maxSlot));
    }
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
