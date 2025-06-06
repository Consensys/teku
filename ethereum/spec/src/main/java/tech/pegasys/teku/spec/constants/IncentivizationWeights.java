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

package tech.pegasys.teku.spec.constants;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class IncentivizationWeights {
  public static final UInt64 TIMELY_SOURCE_WEIGHT = UInt64.valueOf(14);
  public static final UInt64 TIMELY_TARGET_WEIGHT = UInt64.valueOf(26);
  public static final UInt64 TIMELY_HEAD_WEIGHT = UInt64.valueOf(14);
  public static final UInt64 SYNC_REWARD_WEIGHT = UInt64.valueOf(2);
  public static final UInt64 PROPOSER_WEIGHT = UInt64.valueOf(8);
  public static final UInt64 WEIGHT_DENOMINATOR = UInt64.valueOf(64);
}
