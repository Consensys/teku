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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;

public class EngineCapabilitiesMonitorTest {

  private final EventLogger eventLogger = mock(EventLogger.class);
  private final EngineJsonRpcMethodsResolver engineMethodsResolver =
      mock(EngineJsonRpcMethodsResolver.class);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);

  private final List<String> capabilities = List.of("method1", "method2");

  private EngineCapabilitiesMonitor engineCapabilitiesMonitor;

  @BeforeEach
  public void setUp() {
    when(engineMethodsResolver.getCapabilities()).thenReturn(new HashSet<>(capabilities));
    mockEngineCapabilitiesResponse(capabilities);
    engineCapabilitiesMonitor =
        new EngineCapabilitiesMonitor(eventLogger, engineMethodsResolver, executionEngineClient);
  }

  @Test
  public void logsWarningIfEngineDoesNotSupportCapabilities() {
    // engine only supports one of the methods
    mockEngineCapabilitiesResponse(List.of("method1"));

    engineCapabilitiesMonitor.onAvailabilityUpdated(true);

    verify(eventLogger).missingEngineApiCapabilities(List.of("method2"));
  }

  @Test
  public void doesNotLogWarningIfEngineSupportsAllRequiredCapabilities() {
    engineCapabilitiesMonitor.onAvailabilityUpdated(true);

    verifyNoInteractions(eventLogger);
  }

  @Test
  public void doesNotRunMonitoringIfExecutionClientIsNotAvailable() {
    engineCapabilitiesMonitor.onAvailabilityUpdated(false);

    verifyNoInteractions(executionEngineClient);
    verifyNoInteractions(eventLogger);
  }

  private void mockEngineCapabilitiesResponse(final List<String> engineCapabilities) {
    when(executionEngineClient.exchangeCapabilities(capabilities))
        .thenReturn(SafeFuture.completedFuture(new Response<>(engineCapabilities)));
  }
}
