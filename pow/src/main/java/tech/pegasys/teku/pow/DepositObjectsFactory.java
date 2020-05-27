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

package tech.pegasys.teku.pow;

import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.contract.DepositContract;
import tech.pegasys.teku.util.async.AsyncRunner;

public class DepositObjectsFactory {

  private final Eth1Provider eth1Provider;
  private final Eth1EventsChannel eth1EventsChannel;
  private final DepositContract depositContract;
  private final Eth1BlockFetcher eth1BlockFetcher;
  private final AsyncRunner asyncRunner;

  public DepositObjectsFactory(
      Eth1Provider eth1Provider,
      Eth1EventsChannel eth1EventsChannel,
      DepositContract depositContract,
      Eth1BlockFetcher eth1BlockFetcher,
      AsyncRunner asyncRunner) {
    this.eth1Provider = eth1Provider;
    this.eth1EventsChannel = eth1EventsChannel;
    this.depositContract = depositContract;
    this.eth1BlockFetcher = eth1BlockFetcher;
    this.asyncRunner = asyncRunner;
  }

  public DepositFetcher createDepositsFetcher() {
    return new DepositFetcher(
        eth1Provider, eth1EventsChannel, depositContract, eth1BlockFetcher, asyncRunner);
  }

  public DepositProcessingController createDepositProcessingController() {
    return new DepositProcessingController(
        eth1Provider, eth1EventsChannel, asyncRunner, createDepositsFetcher(), eth1BlockFetcher);
  }

  public Eth1DepositManager createEth1DepositsManager() {
    return new Eth1DepositManager(
        eth1Provider,
        asyncRunner,
        eth1EventsChannel,
        createDepositProcessingController(),
        new MinimumGenesisTimeBlockFinder(eth1Provider));
  }
}
