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

package tech.pegasys.teku.validator.api;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ValidatorTimingChannel extends VoidReturningChannelInterface {
  void onSlot(UInt64 slot);

  void onHeadUpdate(
      UInt64 slot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Bytes32 headBlockRoot);

  void onChainReorg(UInt64 newSlot, UInt64 commonAncestorSlot);

  void onPossibleMissedEvents();

  void onBlockProductionDue(UInt64 slot);

  void onAttestationCreationDue(UInt64 slot);

  void onAttestationAggregationDue(UInt64 slot);
}
