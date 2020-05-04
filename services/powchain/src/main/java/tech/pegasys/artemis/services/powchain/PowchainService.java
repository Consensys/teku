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

package tech.pegasys.artemis.services.powchain;

import static tech.pegasys.artemis.util.config.Constants.MAXIMUM_CONCURRENT_ETH1_REQUESTS;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.artemis.pow.DepositContractAccessor;
import tech.pegasys.artemis.pow.DepositObjectsFactory;
import tech.pegasys.artemis.pow.Eth1DataManager;
import tech.pegasys.artemis.pow.Eth1DepositManager;
import tech.pegasys.artemis.pow.Eth1Provider;
import tech.pegasys.artemis.pow.ThrottlingEth1Provider;
import tech.pegasys.artemis.pow.Web3jEth1Provider;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.time.channels.TimeTickChannel;

public class PowchainService extends Service {

  private final Eth1DepositManager eth1DepositManager;
  private final Eth1DataManager eth1DataManager;

  public PowchainService(final ServiceConfig config) {
    ArtemisConfiguration artemisConfig = config.getConfig();

    AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();

    Web3j web3j = Web3j.build(new HttpService(artemisConfig.getEth1Endpoint()));

    final Eth1Provider eth1Provider =
        new ThrottlingEth1Provider(
            new Web3jEth1Provider(web3j, asyncRunner), MAXIMUM_CONCURRENT_ETH1_REQUESTS);

    DepositContractAccessor depositContractAccessor =
        DepositContractAccessor.create(
            eth1Provider, web3j, config.getConfig().getEth1DepositContractAddress().toHexString());

    DepositObjectsFactory depositsObjectFactory =
        new DepositObjectsFactory(
            eth1Provider,
            config.getEventChannels().getPublisher(Eth1EventsChannel.class),
            depositContractAccessor.getContract(),
            asyncRunner);

    eth1DepositManager = depositsObjectFactory.createEth1DepositsManager();

    eth1DataManager =
        new Eth1DataManager(
            eth1Provider,
            config.getEventBus(),
            depositContractAccessor,
            asyncRunner,
            config.getTimeProvider());

    config.getEventChannels().subscribe(TimeTickChannel.class, eth1DataManager);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOfFailFast(
        SafeFuture.fromRunnable(eth1DepositManager::start),
        SafeFuture.fromRunnable(eth1DataManager::start));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(
        SafeFuture.fromRunnable(eth1DepositManager::stop),
        SafeFuture.fromRunnable(eth1DataManager::stop));
  }
}
