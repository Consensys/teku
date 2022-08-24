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

import com.google.common.base.Suppliers;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.Eth1SnapshotLoaderChannel;
import tech.pegasys.teku.ethereum.pow.api.schema.LoadDepositSnapshotResult;
import tech.pegasys.teku.ethereum.pow.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class DepositSnapshotLoader implements Eth1SnapshotLoaderChannel {
  private final DepositSnapshotResourceLoader depositSnapshotResourceLoader =
      new DepositSnapshotResourceLoader();

  private final Optional<String> depositSnapshotResource;
  private final Supplier<SafeFuture<LoadDepositSnapshotResult>> replayResult;

  public DepositSnapshotLoader(final Optional<String> depositSnapshotResource) {
    this.depositSnapshotResource = depositSnapshotResource;
    this.replayResult = Suppliers.memoize(() -> SafeFuture.of(this::loadSnapshot));
  }

  @Override
  public SafeFuture<LoadDepositSnapshotResult> loadDepositSnapshot() {
    return replayResult.get();
  }

  private LoadDepositSnapshotResult loadSnapshot() {
    final Optional<DepositTreeSnapshot> depositTreeSnapshot =
        depositSnapshotResourceLoader.loadDepositSnapshot(depositSnapshotResource);
    if (depositTreeSnapshot.isEmpty() || depositTreeSnapshot.get().getDepositCount() == 0) {
      return LoadDepositSnapshotResult.EMPTY;
    } else {
      return new LoadDepositSnapshotResult(
          depositTreeSnapshot,
          ReplayDepositsResult.create(
              depositTreeSnapshot.get().getExecutionBlockHeight().bigIntegerValue(),
              BigInteger.valueOf(depositTreeSnapshot.get().getDepositCount() - 1),
              true));
    }
  }
}
