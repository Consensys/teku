/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
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
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;

public class PowchainService extends Service implements FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final AtomicBoolean hasInitialized = new AtomicBoolean(false);

  private final ServiceConfig serviceConfig;
  private final PowchainConfiguration powConfig;
  private final Optional<ExecutionWeb3jClientProvider> maybeExecutionWeb3jClientProvider;
  private final Supplier<Optional<BeaconState>> finalizedStateSupplier;

  private AsyncRunner asyncRunner;
  private List<Web3j> web3js;
  private Eth1Providers eth1Providers;
  private Eth1DepositManager eth1DepositManager;
  private Eth1HeadTracker headTracker;

  public PowchainService(
      final ServiceConfig serviceConfig,
      final PowchainConfiguration powConfig,
      final Optional<ExecutionWeb3jClientProvider> maybeExecutionWeb3jClientProvider,
      final Supplier<Optional<BeaconState>> finalizedStateSupplier) {
    checkArgument(powConfig.isEnabled() || maybeExecutionWeb3jClientProvider.isPresent());
    this.serviceConfig = serviceConfig;
    this.powConfig = powConfig;
    this.maybeExecutionWeb3jClientProvider = maybeExecutionWeb3jClientProvider;
    this.finalizedStateSupplier = finalizedStateSupplier;
  }

  @Override
  protected SafeFuture<?> doStart() {
    if (isFormerDepositMechanismDisabled()) {
      // no need to initialize and start services if Eth1 polling has already been disabled
      return SafeFuture.COMPLETE;
    }
    return SafeFuture.fromRunnable(this::initialize)
        .thenPeek(__ -> hasInitialized.set(true))
        .thenCompose(
            __ ->
                SafeFuture.allOfFailFast(
                    SafeFuture.fromRunnable(headTracker::start),
                    SafeFuture.fromRunnable(eth1DepositManager::start),
                    SafeFuture.fromRunnable(eth1Providers::start)));
  }

  @Override
  protected SafeFuture<?> doStop() {
    if (!hasInitialized.get()) {
      return SafeFuture.COMPLETE;
    }
    return SafeFuture.allOfFailFast(
        Stream.concat(
                Stream.<ExceptionThrowingRunnable>of(
                    headTracker::stop,
                    eth1DepositManager::stop,
                    eth1Providers::stop,
                    // stop all tasks currently running
                    asyncRunner::shutdown),
                web3js.stream().map(web3j -> web3j::shutdown))
            .map(SafeFuture::fromRunnable)
            .toArray(SafeFuture[]::new));
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    if (!isRunning()) {
      return;
    }
    if (isFormerDepositMechanismDisabled()) {
      //  stop Eth1 polling
      stop()
          .finish(
              () -> {
                if (hasInitialized.get()) {
                  // only log if polling has been enabled and now stopped
                  STATUS_LOG.eth1PollingHasBeenDisabled();
                }
              },
              LOG::error);
    }
  }

  private boolean isFormerDepositMechanismDisabled() {
    return finalizedStateSupplier
        .get()
        .map(powConfig.getSpec()::isFormerDepositMechanismDisabled)
        .orElse(false);
  }

  @VisibleForTesting
  void initialize() {
    asyncRunner = serviceConfig.createAsyncRunner("powchain");

    final SpecConfig config = powConfig.getSpec().getGenesisSpecConfig();
    if (!powConfig.isEnabled()) {
      final ExecutionWeb3jClientProvider executionWeb3jClientProvider =
          maybeExecutionWeb3jClientProvider.orElseThrow();
      if (executionWeb3jClientProvider.getWeb3JClient().isWebsocketsClient()) {
        throw new InvalidConfigurationException(
            "Eth1 endpoint fallback is not compatible with Websockets execution engine endpoint");
      }
      LOG.info("Eth1 endpoint not provided, using execution engine endpoint for eth1 data");
      web3js = Collections.singletonList(executionWeb3jClientProvider.getWeb3j());

      final Web3jEth1Provider web3jEth1Provider =
          new Web3jEth1Provider(
              config,
              serviceConfig.getMetricsSystem(),
              executionWeb3jClientProvider.getEndpoint(),
              web3js.getFirst(),
              asyncRunner,
              serviceConfig.getTimeProvider());

      eth1Providers =
          Eth1Providers.create(
              Collections.singletonList(web3jEth1Provider),
              asyncRunner,
              serviceConfig.getTimeProvider(),
              serviceConfig.getMetricsSystem());
    } else {
      final OkHttpClient httpClient = createOkHttpClient();
      web3js = createWeb3js(powConfig, httpClient);
      final List<? extends MonitorableEth1Provider> baseProviders =
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
              .toList();
      eth1Providers =
          Eth1Providers.create(
              baseProviders,
              asyncRunner,
              serviceConfig.getTimeProvider(),
              serviceConfig.getMetricsSystem());
    }

    final Eth1Provider eth1Provider = eth1Providers.getEth1Provider();
    final String depositContract = powConfig.getDepositContract().toHexString();
    final DepositEventsAccessor depositEventsAccessor =
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
            powConfig.getEth1LogsMaxBlockRange(),
            serviceConfig.getTimeProvider());

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
    final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
        powConfig.getDepositTreeSnapshotConfiguration();
    final DepositSnapshotFileLoader depositSnapshotFileLoader =
        createDepositSnapshotFileLoader(depositTreeSnapshotConfiguration);
    final StorageQueryChannel storageQueryChannel =
        serviceConfig.getEventChannels().getPublisher(CombinedStorageChannel.class, asyncRunner);
    final DepositSnapshotStorageLoader depositSnapshotStorageLoader =
        new DepositSnapshotStorageLoader(
            depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled(),
            storageQueryChannel);

    eth1DepositManager =
        new Eth1DepositManager(
            config,
            eth1Provider,
            asyncRunner,
            eth1EventsPublisher,
            eth1DepositStorageChannel,
            depositSnapshotFileLoader,
            depositSnapshotStorageLoader,
            depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath().isPresent(),
            depositProcessingController,
            new MinimumGenesisTimeBlockFinder(config, eth1Provider, eth1DepositContractDeployBlock),
            eth1DepositContractDeployBlock,
            headTracker);
  }

  private OkHttpClient createOkHttpClient() {
    final OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            // Increased read timeout allows ETH1 nodes time to process large log requests
            .readTimeout(1, TimeUnit.MINUTES);
    if (LOG.isTraceEnabled()) {
      final HttpLoggingInterceptor logging = new HttpLoggingInterceptor(LOG::trace);
      logging.setLevel(HttpLoggingInterceptor.Level.BODY);
      builder.addInterceptor(logging);
    }
    return builder.build();
  }

  private List<Web3j> createWeb3js(
      final PowchainConfiguration config, final OkHttpClient httpClient) {
    return config.getEth1Endpoints().stream()
        .map(endpoint -> createWeb3j(endpoint, httpClient))
        .toList();
  }

  private Web3j createWeb3j(final String endpoint, final OkHttpClient httpClient) {
    final HttpService web3jService = new HttpService(endpoint, httpClient);
    web3jService.addHeader("User-Agent", VersionProvider.VERSION);
    return Web3j.build(web3jService);
  }

  private DepositSnapshotFileLoader createDepositSnapshotFileLoader(
      final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration) {
    final DepositSnapshotFileLoader.Builder depositSnapshotFileLoaderBuilder =
        new DepositSnapshotFileLoader.Builder();

    if (depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath().isPresent()) {
      depositSnapshotFileLoaderBuilder.addRequiredResource(
          depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath().get());
    } else if (depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()) {
      depositTreeSnapshotConfiguration
          .getCheckpointSyncDepositSnapshotUrl()
          .ifPresent(depositSnapshotFileLoaderBuilder::addOptionalResource);
      depositTreeSnapshotConfiguration
          .getBundledDepositSnapshotPath()
          .ifPresent(depositSnapshotFileLoaderBuilder::addRequiredResource);
    }

    return depositSnapshotFileLoaderBuilder.build();
  }

  @VisibleForTesting
  Eth1DepositManager getEth1DepositManager() {
    return eth1DepositManager;
  }
}
