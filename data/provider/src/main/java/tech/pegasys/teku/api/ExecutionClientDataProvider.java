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

package tech.pegasys.teku.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionChannel;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public class ExecutionClientDataProvider
    implements ExecutionClientEventsChannel, ExecutionClientVersionChannel {

  private final AtomicBoolean isExecutionClientAvailable = new AtomicBoolean(true);
  private final AtomicReference<Optional<ClientVersion>> executionClientVersion =
      new AtomicReference<>(Optional.empty());

  @Override
  public void onAvailabilityUpdated(final boolean isAvailable) {
    isExecutionClientAvailable.set(isAvailable);
  }

  @Override
  public void onExecutionClientVersion(final ClientVersion version) {
    executionClientVersion.set(Optional.of(version));
  }

  @Override
  public void onExecutionClientVersionNotAvailable() {
    executionClientVersion.set(Optional.empty());
  }

  public Optional<ClientVersion> getExecutionClientVersion() {
    return executionClientVersion.get();
  }

  public boolean isExecutionClientAvailable() {
    return isExecutionClientAvailable.get();
  }
}
