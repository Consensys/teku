/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigGloas extends SpecConfigFulu, NetworkingSpecConfigGloas {
  //  Bitwise flag which indicates that a `ValidatorIndex` should be treated as a `BuilderIndex`
  UInt64 BUILDER_INDEX_FLAG = UInt64.valueOf((long) Math.pow(2, 40));
  // Value which indicates the proposer built the payload
  UInt64 BUILDER_INDEX_SELF_BUILD = UInt64.MAX_VALUE;
  UInt64 BUILDER_PAYMENT_THRESHOLD_NUMERATOR = UInt64.valueOf(6);
  UInt64 BUILDER_PAYMENT_THRESHOLD_DENOMINATOR = UInt64.valueOf(10);

  static SpecConfigGloas required(final SpecConfig specConfig) {
    return specConfig
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Gloas spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  int getMinBuilderWithdrawabilityDelay();

  int getPayloadAttestationDueBps();

  int getPtcSize();

  int getMaxPayloadAttestations();

  long getBuilderRegistryLimit();

  long getBuilderPendingWithdrawalsLimit();

  int getMaxBuildersPerWithdrawalsSweep();

  @Override
  Optional<SpecConfigGloas> toVersionGloas();
}
