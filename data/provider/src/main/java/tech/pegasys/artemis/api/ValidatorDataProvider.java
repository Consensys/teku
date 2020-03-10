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

package tech.pegasys.artemis.api;

import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class ValidatorDataProvider {
  private final CombinedChainDataClient combinedChainDataClient;
  private final ValidatorCoordinator validatorCoordinator;


  public ValidatorDataProvider(
      final ValidatorCoordinator validatorCoordinator,
      final CombinedChainDataClient combinedChainDataClient) {
    this.validatorCoordinator = validatorCoordinator;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public SafeFuture<List<Committee>> getCommitteesAtEpoch(UnsignedLong epoch) {
    if (!isStoreAvailable()) {
      return completedFuture(List.of());
    }
    return combinedChainDataClient
        .getCommitteeAssignmentAtEpoch(epoch)
        .thenApply(result -> result.stream().map(Committee::new).collect(Collectors.toList()))
        .exceptionally(err -> List.of());
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient != null && combinedChainDataClient.isStoreAvailable();
  }
}
