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
import tech.pegasys.teku.pow.DepositObjectsFactory;
import tech.pegasys.teku.pow.Eth1BlockFetcher;
import tech.pegasys.teku.pow.Eth1DepositManager;
import tech.pegasys.teku.pow.Eth1Provider;
import tech.pegasys.teku.pow.ThrottlingEth1Provider;
import tech.pegasys.teku.pow.Web3jEth1Provider;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class PowchainService extends Service {

  private final Eth1DepositManager eth1DepositManager;

  public PowchainService(final ServiceConfig config) {
    TekuConfiguration tekuConfig = config.getConfig();

    AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();

    Web3j web3j = Web3j.build(new HttpService(tekuConfig.getEth1Endpoint()));

    final Eth1Provider eth1Provider =
        new ThrottlingEth1Provider(
            new Web3jEth1Provider(web3j, asyncRunner), MAXIMUM_CONCURRENT_ETH1_REQUESTS);

    DepositContractAccessor depositContractAccessor =
        DepositContractAccessor.create(
            eth1Provider, web3j, config.getConfig().getEth1DepositContractAddress().toHexString());

    final Eth1EventsChannel eth1EventsChannel =
        config.getEventChannels().getPublisher(Eth1EventsChannel.class);
    final Eth1BlockFetcher eth1BlockFetcher =
        new Eth1BlockFetcher(
            eth1EventsChannel,
            eth1Provider,
            config.getTimeProvider(),
            calculateEth1DataCacheDurationPriorToCurrentTime());
    DepositObjectsFactory depositsObjectFactory =
        new DepositObjectsFactory(
            eth1Provider,
            eth1EventsChannel,
            depositContractAccessor.getContract(),
            eth1BlockFetcher,
            asyncRunner);

    eth1DepositManager = depositsObjectFactory.createEth1DepositsManager();
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.fromRunnable(eth1DepositManager::start);
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.fromRunnable(eth1DepositManager::stop);
  }
}
