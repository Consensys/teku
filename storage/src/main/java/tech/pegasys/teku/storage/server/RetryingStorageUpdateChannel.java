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

package tech.pegasys.teku.storage.server;

import java.util.Collection;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;

public class RetryingStorageUpdateChannel implements StorageUpdateChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final StorageUpdateChannel delegate;

  public RetryingStorageUpdateChannel(final StorageUpdateChannel delegate) {
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<Void> onStorageUpdate(final StorageUpdate event) {
    return retry(delegate::onStorageUpdate, event);
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
    return retry(delegate::onFinalizedBlocks, finalizedBlocks);
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(
      final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return retry(delegate::onWeakSubjectivityUpdate, weakSubjectivityUpdate);
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {
    retry(
        arg -> {
          delegate.onChainInitialized(arg);
          return SafeFuture.COMPLETE;
        },
        initialAnchor)
    .reportExceptions();
  }

  private <T> SafeFuture<Void> retry(final Function<T, SafeFuture<Void>> method, final T arg) {
    while (true) {
      try {
        final SafeFuture<Void> result = method.apply(arg);
        result.join();
        return result;
      } catch (final Throwable t) {
        LOG.error("Storage update failed. Retrying.", t);
      }
    }
  }
}
