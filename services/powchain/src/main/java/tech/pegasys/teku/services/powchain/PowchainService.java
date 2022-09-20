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

package tech.pegasys.teku.services.powchain;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.beacon.pow.api.Eth1DataCachePeriodCalculator.calculateEth1DataCacheDurationPriorToCurrentTime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.beacon.pow.DepositEventsAccessor;
import tech.pegasys.teku.beacon.pow.DepositFetcher;
import tech.pegasys.teku.beacon.pow.DepositProcessingController;
import tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader;
import tech.pegasys.teku.beacon.pow.DepositSnapshotStorageLoader;
import tech.pegasys.teku.beacon.pow.Eth1BlockFetcher;
import tech.pegasys.teku.beacon.pow.Eth1DepositManager;
import tech.pegasys.teku.beacon.pow.Eth1HeadTracker;
import tech.pegasys.teku.beacon.pow.Eth1Provider;
import tech.pegasys.teku.beacon.pow.MinimumGenesisTimeBlockFinder;
import tech.pegasys.teku.beacon.pow.MonitorableEth1Provider;
import tech.pegasys.teku.beacon.pow.TimeBasedEth1HeadTracker;
import tech.pegasys.teku.beacon.pow.ValidatingEth1EventsPublisher;
import tech.pegasys.teku.beacon.pow.Web3jEth1Provider;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;

public class PowchainService extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1DepositManager eth1DepositManager;
  private final Eth1HeadTracker headTracker;
  private final List<Web3j> web3js;
  private final OkHttpClient okHttpClient;
  private Eth1Providers eth1Providers;

  public PowchainService(
      final ServiceConfig serviceConfig,
      final PowchainConfiguration powConfig,
      final Optional<ExecutionWeb3jClientProvider> maybeExecutionWeb3jClientProvider,
      final boolean asyncStorage) {
    checkArgument(powConfig.isEnabled() || maybeExecutionWeb3jClientProvider.isPresent());

    AsyncRunner asyncRunner = serviceConfig.createAsyncRunner("powchain");

    this.okHttpClient = createOkHttpClient();
    final SpecConfig config = powConfig.getSpec().getGenesisSpecConfig();
    if (!powConfig.isEnabled()) {
      final ExecutionWeb3jClientProvider executionWeb3jClientProvider =
          maybeExecutionWeb3jClientProvider.orElseThrow();
      if (executionWeb3jClientProvider.getWeb3JClient().isWebsocketsClient()) {
        throw new InvalidConfigurationException(
            "Eth1 endpoint fallback is not compatible with Websockets execution engine endpoint");
      }
      LOG.info("Eth1 endpoint not provided, using execution engine endpoint for eth1 data");
      this.web3js = Collections.singletonList(executionWeb3jClientProvider.getWeb3j());

      final Web3jEth1Provider web3jEth1Provider =
          new Web3jEth1Provider(
              powConfig.getSpec().getGenesisSpecConfig(),
              serviceConfig.getMetricsSystem(),
              executionWeb3jClientProvider.getEndpoint(),
              web3js.get(0),
              asyncRunner,
              serviceConfig.getTimeProvider());

      eth1Providers =
          Eth1Providers.create(
              Collections.singletonList(web3jEth1Provider),
              asyncRunner,
              serviceConfig.getTimeProvider(),
              serviceConfig.getMetricsSystem());
    } else {
      this.web3js = createWeb3js(powConfig);
      final List<MonitorableEth1Provider> baseProviders =
          IntStream.range(0, web3js.size())
              .mapToObj(
                  idx ->
                      new Web3jEth1Provider(
                          config,
                          serviceConfig.getMetricsSystem(),
                          Eth1Provider.generateEth1ProviderId(
                              idx + 1, powConfig.getEth1Endpoints().get(idx)),
                          web3js.get(idx),
                          asyncRunner,
                          serviceConfig.getTimeProvider()))
              .collect(Collectors.toList());
      eth1Providers =
          Eth1Providers.create(
              baseProviders,
              asyncRunner,
              serviceConfig.getTimeProvider(),
              serviceConfig.getMetricsSystem());
    }

    final Eth1Provider eth1Provider = eth1Providers.getEth1Provider();
    final String depositContract = powConfig.getDepositContract().toHexString();
    DepositEventsAccessor depositEventsAccessor =
        new DepositEventsAccessor(eth1Provider, depositContract);

    final ValidatingEth1EventsPublisher eth1EventsPublisher =
        new ValidatingEth1EventsPublisher(
            serviceConfig.getEventChannels().getPublisher(Eth1EventsChannel.class));
    final Eth1DepositStorageChannel eth1DepositStorageChannel =
        serviceConfig.getEventChannels().getPublisher(Eth1DepositStorageChannel.class, asyncRunner);
    final Eth1BlockFetcher eth1BlockFetcher =
        new Eth1BlockFetcher(
            eth1EventsPublisher,
            eth1Provider,
            serviceConfig.getTimeProvider(),
            calculateEth1DataCacheDurationPriorToCurrentTime(config));

    final DepositFetcher depositFetcher =
        new DepositFetcher(
            eth1Provider,
            eth1EventsPublisher,
            depositEventsAccessor,
            eth1BlockFetcher,
            asyncRunner,
            powConfig.getEth1LogsMaxBlockRange());

    headTracker =
        new TimeBasedEth1HeadTracker(
            powConfig.getSpec(), serviceConfig.getTimeProvider(), asyncRunner, eth1Provider);
    final DepositProcessingController depositProcessingController =
        new DepositProcessingController(
            config,
            eth1Provider,
            eth1EventsPublisher,
            asyncRunner,
            depositFetcher,
            eth1BlockFetcher,
            headTracker);

    final Optional<UInt64> eth1DepositContractDeployBlock =
        powConfig.getDepositContractDeployBlock();
    final DepositSnapshotFileLoader depositSnapshotFileLoader =
        new DepositSnapshotFileLoader(powConfig.getDepositSnapshotPath());
    final StorageQueryChannel storageQueryChannel;
    if (asyncStorage) {
      storageQueryChannel =
          serviceConfig.getEventChannels().getPublisher(CombinedStorageChannel.class, asyncRunner);
    } else {
      storageQueryChannel =
          serviceConfig.getEventChannels().getPublisher(StorageQueryChannel.class, asyncRunner);
    }
    final DepositSnapshotStorageLoader depositSnapshotStorageLoader =
        new DepositSnapshotStorageLoader(
            powConfig.isDepositSnapshotStorageEnabled(), storageQueryChannel);

    eth1DepositManager =
        new Eth1DepositManager(
            config,
            eth1Provider,
            asyncRunner,
            eth1EventsPublisher,
            eth1DepositStorageChannel,
            depositSnapshotFileLoader,
            depositSnapshotStorageLoader,
            depositProcessingController,
            new MinimumGenesisTimeBlockFinder(config, eth1Provider, eth1DepositContractDeployBlock),
            eth1DepositContractDeployBlock,
            headTracker);
  }

  private List<Web3j> createWeb3js(final PowchainConfiguration config) {
    return config.getEth1Endpoints().stream().map(this::createWeb3j).collect(Collectors.toList());
  }

  private Web3j createWeb3j(final String endpoint) {
    final HttpService web3jService = new HttpService(endpoint, this.okHttpClient);
    web3jService.addHeader("User-Agent", VersionProvider.VERSION);
    return Web3j.build(web3jService);
  }

  private static OkHttpClient createOkHttpClient() {
    final OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            // Increased read timeout allows ETH1 nodes time to process large log requests
            .readTimeout(1, TimeUnit.MINUTES);
    if (LOG.isTraceEnabled()) {
      HttpLoggingInterceptor logging = new HttpLoggingInterceptor(LOG::trace);
      logging.setLevel(HttpLoggingInterceptor.Level.BODY);
      builder.addInterceptor(logging);
    }
    return builder.build();
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOfFailFast(
        SafeFuture.fromRunnable(headTracker::start),
        SafeFuture.fromRunnable(eth1DepositManager::start),
        SafeFuture.fromRunnable(eth1Providers::start));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOfFailFast(
        Stream.concat(
                Stream.<ExceptionThrowingRunnable>of(
                    headTracker::stop, eth1DepositManager::stop, eth1Providers::stop),
                web3js.stream().map(web3j -> web3j::shutdown))
            .map(SafeFuture::fromRunnable)
            .toArray(SafeFuture[]::new));
  }
}
