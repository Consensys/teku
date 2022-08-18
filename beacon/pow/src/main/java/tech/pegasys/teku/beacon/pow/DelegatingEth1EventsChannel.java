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

package tech.pegasys.teku.beacon.pow;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DelegatingEth1EventsChannel implements Eth1EventsChannel {
  protected final Eth1EventsChannel delegate;

  public DelegatingEth1EventsChannel(final Eth1EventsChannel delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    delegate.onDepositsFromBlock(event);
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    delegate.onMinGenesisTimeBlock(event);
  }

  @Override
  public void onEth1Block(
      final UInt64 blockHeight, final Bytes32 blockHash, final UInt64 blockTimestamp) {
    delegate.onEth1Block(blockHeight, blockHash, blockTimestamp);
  }
}
