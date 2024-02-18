/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.executionlayer;

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;

public class EngineCapabilitiesMonitor implements ExecutionClientEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final EventLogger eventLogger;
  private final Supplier<List<String>> capabilitiesSupplier;
  private final ExecutionEngineClient executionEngineClient;

  public EngineCapabilitiesMonitor(
      final EventLogger eventLogger,
      final EngineJsonRpcMethodsResolver engineMethodsResolver,
      final ExecutionEngineClient executionEngineClient) {
    this.eventLogger = eventLogger;
    this.capabilitiesSupplier =
        Suppliers.memoize(() -> new ArrayList<>(engineMethodsResolver.getCapabilities()));
    this.executionEngineClient = executionEngineClient;
  }

  @Override
  public void onAvailabilityUpdated(final boolean isAvailable) {
    if (isAvailable) {
      monitor().finish(error -> LOG.error("Exception exchanging Engine API capabilities", error));
    }
  }

  private SafeFuture<Void> monitor() {
    final List<String> capabilities = capabilitiesSupplier.get();
    return executionEngineClient
        .exchangeCapabilities(capabilities)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenAccept(
            engineCapabilities -> {
              LOG.debug("Engine API capabilities response: " + engineCapabilities);
              final List<String> missingEngineCapabilities =
                  capabilities.stream()
                      .filter(capability -> !engineCapabilities.contains(capability))
                      .toList();
              if (!missingEngineCapabilities.isEmpty()) {
                eventLogger.missingEngineApiCapabilities(missingEngineCapabilities);
              }
            });
  }
}
