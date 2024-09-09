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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.NetworkingSpecConfigEip7594;

public interface MinCustodyPeriodSlotCalculator {

  static MinCustodyPeriodSlotCalculator createFromSpec(Spec spec) {
    return currentSlot -> {
      UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
      int custodyPeriodEpochs =
          spec.getSpecConfig(currentEpoch)
              .toVersionEip7594()
              .map(NetworkingSpecConfigEip7594::getMinEpochsForDataColumnSidecarsRequests)
              .orElse(0);
      if (custodyPeriodEpochs == 0) {
        return currentSlot;
      } else {
        UInt64 minCustodyEpoch = currentEpoch.minusMinZero(custodyPeriodEpochs);
        return spec.computeStartSlotAtEpoch(minCustodyEpoch);
      }
    };
  }

  UInt64 getMinCustodyPeriodSlot(UInt64 currentSlot);
}
