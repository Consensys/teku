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

package tech.pegasys.teku.statetransition.util;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.time.Duration;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.datacolumns.CurrentSlotProvider;
import tech.pegasys.teku.storage.client.RecentChainData;

@FunctionalInterface
public interface RPCFetchDelayProvider {
  // RPC fetching delay timings
  long DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS = 1500L;
  UInt64 DEFAULT_MIN_WAIT_MILLIS = UInt64.valueOf(500);
  UInt64 DEFAULT_TARGET_WAIT_MILLIS = UInt64.valueOf(1000);

  RPCFetchDelayProvider NO_DELAY = slot -> Duration.ZERO;

  static RPCFetchDelayProvider create(
      final Spec spec,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final CurrentSlotProvider currentSlotProvider,
      final long maxWaitRelativeToAttDueMillis,
      final UInt64 minWaitMillis,
      final UInt64 targetWaitMillis) {

    return (slot) -> {
      if (slot.isLessThan(currentSlotProvider.getCurrentSlot())) {
        // old slot
        return Duration.ZERO;
      }

      final UInt64 nowMillis = timeProvider.getTimeInMillis();
      final UInt64 slotStartTimeMillis = secondsToMillis(recentChainData.computeTimeAtSlot(slot));
      final UInt64 attestationDueMillis =
          slotStartTimeMillis.plus(spec.getAttestationDueMillis(slot));

      if (nowMillis.isGreaterThanOrEqualTo(attestationDueMillis)) {
        // late block, we already produced attestations on previous head,
        // so let's wait our target delay before trying to fetch
        return Duration.ofMillis(targetWaitMillis.intValue());
      }

      final UInt64 upperLimitRelativeToAttDue =
          attestationDueMillis.minus(maxWaitRelativeToAttDueMillis);

      final UInt64 targetMillis = nowMillis.plus(targetWaitMillis);

      final UInt64 finalTime =
          targetMillis.min(upperLimitRelativeToAttDue).max(nowMillis.plus(minWaitMillis));

      return Duration.ofMillis(finalTime.minus(nowMillis).intValue());
    };
  }

  Duration calculate(UInt64 slot);
}
