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

package tech.pegasys.teku.pow.api;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;

public interface Eth1EventsChannel extends VoidReturningChannelInterface {
  void onDepositsFromBlock(DepositsFromBlockEvent event);

  void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event);

  default void onEth1Block(Bytes32 blockHash, UInt64 blockTimestamp) {}
}
