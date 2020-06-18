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

package tech.pegasys.teku.services.powchain;

import static tech.pegasys.teku.pow.api.Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToCurrentTime;
import static tech.pegasys.teku.util.config.Constants.MAXIMUM_CONCURRENT_ETH1_REQUESTS;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.pow.DepositContractAccessor;
import tech.pegasys.teku.pow.DepositFetcher;
import tech.pegasys.teku.pow.DepositProcessingController;
import tech.pegasys.teku.pow.ErrorTrackingEth1Provider;
import tech.pegasys.teku.pow.Eth1BlockFetcher;
import tech.pegasys.teku.pow.Eth1DepositManager;
import tech.pegasys.teku.pow.Eth1HeadTracker;
import tech.pegasys.teku.pow.Eth1Provider;
import tech.pegasys.teku.pow.MinimumGenesisTimeBlockFinder;
import tech.pegasys.teku.pow.ThrottlingEth1Provider;
import tech.pegasys.teku.pow.Web3jEth1Provider;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class PowchainService extends Service {

  private final Eth1DepositManager eth1DepositManager;
  private final Eth1HeadTracker headTracker;

  public PowchainService(final ServiceConfig config) {
    TekuConfiguration tekuConfig = config.getConfig();

    AsyncRunner asyncRunner = config.createAsyncRunner("powchain");

    Web3j web3j = Web3j.build(new HttpService(tekuConfig.getEth1Endpoint()));

    final Eth1Provider eth1Provider =
        new ThrottlingEth1Provider(
            new ErrorTrackingEth1Provider(
                new Web3jEth1Provider(web3j, asyncRunner), asyncRunner, config.getTimeProvider()),
            MAXIMUM_CONCURRENT_ETH1_REQUESTS);

    DepositContractAccessor depositContractAccessor =
        DepositContractAccessor.create(
            eth1Provider, web3j, config.getConfig().getEth1DepositContractAddress().toHexString());

    final Eth1EventsChannel eth1EventsChannel =
        config.getEventChannels().getPublisher(Eth1EventsChannel.class);
    final Eth1DepositStorageChannel eth1DepositStorageChannel =
        config.getEventChannels().getPublisher(Eth1DepositStorageChannel.class);
    final Eth1BlockFetcher eth1BlockFetcher =
        new Eth1BlockFetcher(
            eth1EventsChannel,
            eth1Provider,
            config.getTimeProvider(),
            calculateEth1DataCacheDurationPriorToCurrentTime());

    final DepositFetcher depositFetcher =
        new DepositFetcher(
            eth1Provider,
            eth1EventsChannel,
            depositContractAccessor.getContract(),
            eth1BlockFetcher,
            asyncRunner);

    headTracker = new Eth1HeadTracker(asyncRunner, eth1Provider);
    final DepositProcessingController depositProcessingController =
        new DepositProcessingController(
            eth1Provider,
            eth1EventsChannel,
            asyncRunner,
            depositFetcher,
            eth1BlockFetcher,
            headTracker);

    eth1DepositManager =
        new Eth1DepositManager(
            eth1Provider,
            asyncRunner,
            eth1EventsChannel,
            eth1DepositStorageChannel,
            depositProcessingController,
            new MinimumGenesisTimeBlockFinder(eth1Provider));
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOfFailFast(
        SafeFuture.fromRunnable(headTracker::start),
        SafeFuture.fromRunnable(eth1DepositManager::start));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOfFailFast(
        SafeFuture.fromRunnable(headTracker::stop),
        SafeFuture.fromRunnable(eth1DepositManager::stop));
  }
}
