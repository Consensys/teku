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

import java.util.Optional;

public class DepositTreeSnapshotConfiguration {
  private final Optional<String> customDepositSnapshotPath;
  private final Optional<String> checkpointSyncDepositSnapshotUrl;
  private final Optional<String> bundledDepositSnapshotPath;
  private final boolean isBundledDepositSnapshotEnabled;

  DepositTreeSnapshotConfiguration(
      final Optional<String> customDepositSnapshotPath,
      final Optional<String> checkpointSyncDepositSnapshotUrl,
      final Optional<String> bundledDepositSnapshotPath,
      final boolean isBundledDepositSnapshotEnabled) {
    this.customDepositSnapshotPath = customDepositSnapshotPath;
    this.checkpointSyncDepositSnapshotUrl = checkpointSyncDepositSnapshotUrl;
    this.bundledDepositSnapshotPath = bundledDepositSnapshotPath;
    this.isBundledDepositSnapshotEnabled = isBundledDepositSnapshotEnabled;
  }

  public Optional<String> getCustomDepositSnapshotPath() {
    return customDepositSnapshotPath;
  }

  public Optional<String> getCheckpointSyncDepositSnapshotUrl() {
    return checkpointSyncDepositSnapshotUrl;
  }

  public Optional<String> getBundledDepositSnapshotPath() {
    return bundledDepositSnapshotPath;
  }

  public boolean isBundledDepositSnapshotEnabled() {
    return isBundledDepositSnapshotEnabled;
  }
}
