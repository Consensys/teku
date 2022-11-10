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

package tech.pegasys.teku.ethereum.pow.api.schema;

import java.math.BigInteger;
import java.util.Optional;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;

public class LoadDepositSnapshotResult {
  public static final LoadDepositSnapshotResult EMPTY =
      new LoadDepositSnapshotResult(Optional.empty(), ReplayDepositsResult.empty());

  private final Optional<DepositTreeSnapshot> depositTreeSnapshot;

  private final ReplayDepositsResult replayDepositsResult;

  public LoadDepositSnapshotResult(
      final Optional<DepositTreeSnapshot> depositTreeSnapshot,
      final ReplayDepositsResult replayDepositsResult) {
    this.depositTreeSnapshot = depositTreeSnapshot;
    this.replayDepositsResult = replayDepositsResult;
  }

  public static LoadDepositSnapshotResult create(
      final Optional<DepositTreeSnapshot> depositTreeSnapshot) {
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

  public Optional<DepositTreeSnapshot> getDepositTreeSnapshot() {
    return depositTreeSnapshot;
  }

  public ReplayDepositsResult getReplayDepositsResult() {
    return replayDepositsResult;
  }
}
