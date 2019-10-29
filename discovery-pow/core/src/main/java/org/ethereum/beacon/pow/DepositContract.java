/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.pow;

import com.google.common.base.Objects;
import java.util.List;
import java.util.Optional;
import org.ethereum.beacon.consensus.ChainStart;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.state.Eth1Data;
import org.reactivestreams.Publisher;
import tech.pegasys.artemis.ethereum.core.Hash32;

/** Interface to the Eth1.0 Deposit contract */
public interface DepositContract {

  /**
   * Returns the '0|1 events' stream publisher to listen to <code>ChainStart</code> event The
   * listening process starts lazily when at least one subscriber subscribes. The process takes into
   * account the <code>distanceFromHead</code> parameter, so the <code>ChainStart</code> event is
   * issued only when specified number of confirmation blocks are imported.
   */
  Publisher<ChainStart> getChainStartMono();

  /**
   * Returns stream with all deposits received by Deposit Contract (Deposit event). The listening
   * process starts lazily when at least one subscriber subscribes. The process takes into account
   * the <code>distanceFromHead</code> parameter, so the <code>ChainStart</code> event is issued
   * only when specified number of confirmation blocks are imported.
   */
  Publisher<Deposit> getDepositStream();

  /**
   * Returns a list of deposits found in already imported blocks. The method takes into account the
   * <code>distanceFromHead</code> parameter, so only events from confirmed blocks are returned.
   * NOTE: this is a blocking call which may execute significant amount of time since it's scanning
   * Blocks database
   *
   * @param maxCount Maximum number of returned events
   * @param fromDepositExclusive effectively the last deposit 'coordinates' which was included into
   *     blocks
   * @param tillDepositInclusive effectively the last voted <code>Eth1Data</code> from the <code>
   *     BeaconState</code>
   * @return requested deposit infos. Empty list if <code>fromDeposit == tillDeposit</code> od the
   *     blockchain is out of sync and <code>tillDeposit</code> block is not imported yet
   */
  List<DepositInfo> peekDeposits(
      int maxCount, Eth1Data fromDepositExclusive, Eth1Data tillDepositInclusive);

  /**
   * Checks if the block with <code>blockHash</code> contains <code>DepositEvent</code> with the
   * specified <code>depositRoot</code> The method takes into account the <code>distanceFromHead
   * </code> parameter, so only events from confirmed blocks are checked. Even if the corresponding
   * block is imported and contains the requested <code>depositRoot</code> but is not confirmed yet
   * the method should return <code>false</code>
   *
   * @param blockHash Block hash where depositRoot event is looked for
   * @param depositRoot The root of the <code>DepositEvent</code>
   */
  boolean hasDepositRoot(Hash32 blockHash, Hash32 depositRoot);

  /**
   * Returns the last found <code>DepositEvent</code> 'coordinates' The method takes into account
   * the <code>distanceFromHead</code> parameter, so only events from confirmed blocks are
   * considered. NOTE: this is a blocking call which may execute significant amount of time since
   * it's scanning Blocks database
   *
   * @return <code>{@link Optional#empty()}</code> if <code>ChainStart</code> event is not yet
   *     issued
   */
  Optional<Eth1Data> getLatestEth1Data();

  /**
   * Sets the number of block confirmations which is required for any contract event to be
   * considered as existing
   */
  void setDistanceFromHead(long distanceFromHead);

  /** Container for a pair of {@link Deposit} and its 'coordinates' {@link Eth1Data} */
  class DepositInfo {
    private final Deposit deposit;
    private final Eth1Data eth1Data;

    public DepositInfo(Deposit deposit, Eth1Data eth1Data) {
      this.deposit = deposit;
      this.eth1Data = eth1Data;
    }

    public Deposit getDeposit() {
      return deposit;
    }

    public Eth1Data getEth1Data() {
      return eth1Data;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DepositInfo that = (DepositInfo) o;
      return Objects.equal(deposit, that.deposit) && Objects.equal(eth1Data, that.eth1Data);
    }
  }
}
