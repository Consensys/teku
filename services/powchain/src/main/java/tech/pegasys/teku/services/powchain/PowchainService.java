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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader;
import tech.pegasys.teku.beacon.pow.DepositSnapshotStorageLoader;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.schema.LoadDepositSnapshotResult;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;

/**
 * Loads the finalized deposit-tree snapshot at startup and seeds the {@link Eth1EventsChannel}
 * consumers (notably the deposit provider) with it. Runtime deposit-log fetching over web3j has
 * been removed; deposits now arrive via in-protocol execution requests (EIP-6110), so this service
 * only needs to provide the initial snapshot used for genesis and checkpoint sync.
 */
public class PowchainService extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final ServiceConfig serviceConfig;
  private final PowchainConfiguration powConfig;

  public PowchainService(final ServiceConfig serviceConfig, final PowchainConfiguration powConfig) {
    this.serviceConfig = serviceConfig;
    this.powConfig = powConfig;
  }

  @Override
  protected SafeFuture<?> doStart() {
    final Eth1EventsChannel eth1EventsChannel =
        serviceConfig.getEventChannels().getPublisher(Eth1EventsChannel.class);
    return loadDepositSnapshot()
        .thenAccept(
            result ->
                result
                    .getDepositTreeSnapshot()
                    .ifPresentOrElse(
                        depositTreeSnapshot -> {
                          STATUS_LOG.loadedDepositSnapshot(
                              depositTreeSnapshot.getDepositCount(),
                              depositTreeSnapshot.getExecutionBlockHash());
                          eth1EventsChannel.onInitialDepositTreeSnapshot(depositTreeSnapshot);
                        },
                        () -> LOG.debug("No deposit tree snapshot available to load")));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  private SafeFuture<LoadDepositSnapshotResult> loadDepositSnapshot() {
    final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
        powConfig.getDepositTreeSnapshotConfiguration();
    final DepositSnapshotFileLoader depositSnapshotFileLoader =
        createDepositSnapshotFileLoader(depositTreeSnapshotConfiguration);

    if (depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath().isPresent()) {
      LOG.debug("Custom deposit snapshot is provided, using it");
      return SafeFuture.completedFuture(depositSnapshotFileLoader.loadDepositSnapshot());
    }

    final AsyncRunner asyncRunner = serviceConfig.createAsyncRunner("powchain");
    final StorageQueryChannel storageQueryChannel =
        serviceConfig.getEventChannels().getPublisher(CombinedStorageChannel.class, asyncRunner);
    final DepositSnapshotStorageLoader depositSnapshotStorageLoader =
        new DepositSnapshotStorageLoader(
            depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled(),
            storageQueryChannel);

    return depositSnapshotStorageLoader
        .loadDepositSnapshot()
        .thenApply(
            storageDepositSnapshotResult -> {
              if (storageDepositSnapshotResult.getDepositTreeSnapshot().isPresent()) {
                return storageDepositSnapshotResult;
              }
              LOG.debug(
                  "Database deposit snapshot is not present, falling back to the file loader");
              return depositSnapshotFileLoader.loadDepositSnapshot();
            });
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
}
