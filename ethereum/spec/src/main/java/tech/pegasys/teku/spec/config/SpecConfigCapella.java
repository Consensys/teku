/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.config;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigCapella extends SpecConfigBellatrix {

  static SpecConfigCapella required(SpecConfig specConfig) {
    return specConfig
        .toVersionCapella()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Capella spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  Bytes4 getCapellaForkVersion();

  UInt64 getCapellaForkEpoch();

  @Override
  Optional<SpecConfigCapella> toVersionCapella();

  int getMaxBlsToExecutionChanges();

  int getMaxWithdrawalsPerPayload();

  int getMaxValidatorsPerWithdrawalSweep();
}
