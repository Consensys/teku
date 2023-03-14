/*
 * Copyright ConsenSys Software Inc., 2023
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.executionlayer.ExecutionClientEngineApiCapabilitiesProvider.EXCHANGE_CAPABILITIES_ATTEMPTS_BEFORE_LOG_WARN;
import static tech.pegasys.teku.ethereum.executionlayer.ExecutionClientEngineApiCapabilitiesProvider.EXCHANGE_CAPABILITIES_FETCH_INTERVAL_IN_SECONDS;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.Spec;

class ExecutionClientEngineApiCapabilitiesProviderTest {

  private LogCaptor logCaptor;
  private StubTimeProvider timeProvider;
  private StubAsyncRunner asyncRunner;
  private EventChannels eventChannels;
  private ExecutionEngineClient executionEngineClient;
  private EngineApiCapabilitiesProvider localCapabilitiesProvider;

  @BeforeEach
  public void setUp() {
    logCaptor = LogCaptor.forClass(ExecutionClientEngineApiCapabilitiesProvider.class);
    timeProvider = StubTimeProvider.withTimeInSeconds(0);
    asyncRunner = new StubAsyncRunner();
    eventChannels = mock(EventChannels.class);
    executionEngineClient = mock(ExecutionEngineClient.class);
    localCapabilitiesProvider = new StubEngineApiCapabilitiesProvider(arbitraryListOfMethods());
  }

  @AfterEach
  public void tearDown() {
    logCaptor.clearLogs();
  }

  private ExecutionClientEngineApiCapabilitiesProvider
      createRemoteCapabilitiesProviderWithMatchingCapabilities(
          final Collection<EngineJsonRpcMethod<?>> methods) {
    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        new ExecutionClientEngineApiCapabilitiesProvider(
            asyncRunner,
            executionEngineClient,
            new StubEngineApiCapabilitiesProvider(methods),
            eventChannels);

    // Registering EL as available will schedule the first fetch (won't run until asyncRunner is
    // triggered)
    remoteCapabilitiesProvider.onAvailabilityUpdated(true);

    // EL will respond with same list of local methods
    mockExecutionEngineClientForCapabilities(asVersionedNames(methods));

    return remoteCapabilitiesProvider;
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldSendLocalSupportedMethodsWhenFetchingSupportedRemoteMethods() {
    final List<EngineJsonRpcMethod<?>> expectedLocalSupportedMethods = arbitraryListOfMethods();

    createRemoteCapabilitiesProviderWithMatchingCapabilities(expectedLocalSupportedMethods);
    asyncRunner.executeQueuedActions();

    final ArgumentCaptor<List<String>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(executionEngineClient).exchangeCapabilities(argumentCaptor.capture());
    final List<String> localSupportedMethodsNamesSent = argumentCaptor.getValue();
    assertThat(localSupportedMethodsNamesSent)
        .containsExactlyInAnyOrderElementsOf(asVersionedNames(expectedLocalSupportedMethods));
  }

  @Test
  public void supportedRemoteMethodsShouldMatchResponseFromExchangeCapabilities() {
    final List<EngineJsonRpcMethod<?>> expectedRemoteSupportedMethods = arbitraryListOfMethods();

    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        createRemoteCapabilitiesProviderWithMatchingCapabilities(expectedRemoteSupportedMethods);
    asyncRunner.executeQueuedActions();

    final Collection<EngineJsonRpcMethod<?>> remotedSupportedMethodNames =
        remoteCapabilitiesProvider.supportedMethods();

    assertThat(remotedSupportedMethodNames)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrderElementsOf(expectedRemoteSupportedMethods);
  }

  @Test
  public void supportedRemoteMethodsResponseShouldBeIntersectionWithLocalValues() {
    final EngineJsonRpcMethod<?> method1 = new EngineNewPayloadV1(executionEngineClient);
    final EngineJsonRpcMethod<?> method2 = new EngineNewPayloadV2(executionEngineClient);
    final EngineJsonRpcMethod<?> method3 = new EngineNewPayloadV3(executionEngineClient);

    final StubEngineApiCapabilitiesProvider localCapabilitiesProvider =
        new StubEngineApiCapabilitiesProvider(List.of(method2, method3));
    mockExecutionEngineClientForCapabilities(asVersionedNames(List.of(method1, method2)));

    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        new ExecutionClientEngineApiCapabilitiesProvider(
            asyncRunner, executionEngineClient, localCapabilitiesProvider, eventChannels);
    remoteCapabilitiesProvider.onAvailabilityUpdated(true);
    asyncRunner.executeQueuedActions();

    assertThat(remoteCapabilitiesProvider.supportedMethods())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrderElementsOf(List.of(method2));
  }

  @Test
  public void supportedRemoteMethodsResponseShouldBeMergedWithExistingSupportedRemoteValues() {
    final EngineJsonRpcMethod<?> method1 = new EngineNewPayloadV1(executionEngineClient);
    final EngineJsonRpcMethod<?> method2 = new EngineNewPayloadV2(executionEngineClient);
    final EngineJsonRpcMethod<?> method3 = new EngineNewPayloadV3(executionEngineClient);

    final StubEngineApiCapabilitiesProvider localCapabilitiesProvider =
        new StubEngineApiCapabilitiesProvider(List.of(method1, method2, method3));
    when(executionEngineClient.exchangeCapabilities(any()))
        .thenReturn(SafeFuture.completedFuture(new Response<>(asVersionedNames(List.of(method1)))))
        .thenReturn(SafeFuture.completedFuture(new Response<>(asVersionedNames(List.of(method2)))));

    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        new ExecutionClientEngineApiCapabilitiesProvider(
            asyncRunner, executionEngineClient, localCapabilitiesProvider, eventChannels);
    remoteCapabilitiesProvider.onAvailabilityUpdated(true);
    asyncRunner.executeQueuedActions();

    assertThat(remoteCapabilitiesProvider.supportedMethods())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrderElementsOf(List.of(method1));

    timeProvider.advanceTimeBySeconds(EXCHANGE_CAPABILITIES_FETCH_INTERVAL_IN_SECONDS);
    asyncRunner.executeQueuedActions();

    assertThat(remoteCapabilitiesProvider.supportedMethods())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrderElementsOf(List.of(method2));
  }

  @Test
  public void shouldLogErrorWhenExecutionClientThrowsException() {
    createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());
    when(executionEngineClient.exchangeCapabilities(any()))
        .thenThrow(new RuntimeException("Unexpected Error"));

    asyncRunner.executeQueuedActions();

    assertThat(logCaptor.getErrorLogs())
        .hasSize(1)
        .contains("Unexpected failure fetching remote capabilities from Execution Client");
  }

  @Test
  public void shouldLogWarningWhenExecutionClientResponseFailsNumAttempts() {
    logCaptor.setLogLevelToTrace();

    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    // Initial run
    asyncRunner.executeQueuedActions();
    assertThat(remoteCapabilitiesProvider.supportedMethods()).isNotEmpty();
    reset(executionEngineClient);
    logCaptor.clearLogs();

    final String executionClientErrorMessage = "Invalid Request";
    mockExecutionEngineClientForFailedCapabilitiesResponse(executionClientErrorMessage);
    asyncRunner.executeRepeatedly(EXCHANGE_CAPABILITIES_ATTEMPTS_BEFORE_LOG_WARN);

    verify(executionEngineClient, times(EXCHANGE_CAPABILITIES_ATTEMPTS_BEFORE_LOG_WARN))
        .exchangeCapabilities(any());

    assertThat(logCaptor.getTraceLogs())
        .hasSize(EXCHANGE_CAPABILITIES_ATTEMPTS_BEFORE_LOG_WARN - 1)
        .allMatch(s -> s.contains("Error fetching remote capabilities from Execution Client"));

    assertThat(logCaptor.getWarnLogs())
        .hasSize(1)
        .contains(
            "Error fetching remote capabilities from Execution Client (failed attempts = 3). "
                + executionClientErrorMessage);

    assertThat(logCaptor.getErrorLogs()).isEmpty();
  }

  @Test
  public void shouldLogErrorIfExecutionClientHasNeverRespondedSuccessfully() {
    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    final String executionClientErrorMessage = "Invalid Request";
    mockExecutionEngineClientForFailedCapabilitiesResponse(executionClientErrorMessage);

    asyncRunner.executeQueuedActions();
    assertThat(remoteCapabilitiesProvider.supportedMethods()).isEmpty();

    assertThat(logCaptor.getErrorLogs())
        .hasSize(1)
        .allMatch(s -> s.contains("Unable to fetch remote capabilities from Execution Client"));
  }

  @Test
  public void shouldResetFailedAttemptsCounterAfterASuccessfulResponse() {
    final SafeFuture<Response<List<String>>> successfulResponse =
        SafeFuture.completedFuture(new Response<>(asVersionedNames(arbitraryListOfMethods())));
    final SafeFuture<Response<List<String>>> failedResponse =
        SafeFuture.completedFuture(Response.withErrorMessage("Failed Reason"));

    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    when(executionEngineClient.exchangeCapabilities(any()))
        .thenReturn(successfulResponse)
        .thenReturn(failedResponse)
        .thenReturn(failedResponse)
        .thenReturn(successfulResponse)
        .thenReturn(failedResponse);

    asyncRunner.executeQueuedActions(1);
    assertThat(remoteCapabilitiesProvider.getFailedFetchCount()).isEqualTo(0);
    asyncRunner.executeQueuedActions(1);
    assertThat(remoteCapabilitiesProvider.getFailedFetchCount()).isEqualTo(1);
    asyncRunner.executeQueuedActions(1);
    assertThat(remoteCapabilitiesProvider.getFailedFetchCount()).isEqualTo(2);
    asyncRunner.executeQueuedActions(1);
    assertThat(remoteCapabilitiesProvider.getFailedFetchCount()).isEqualTo(0);
    asyncRunner.executeQueuedActions(1);
    assertThat(remoteCapabilitiesProvider.getFailedFetchCount()).isEqualTo(1);

    verify(executionEngineClient, times(5)).exchangeCapabilities(any());
  }

  @Test
  public void shouldSubscribeToExecutionClientEvents() {
    createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    verify(eventChannels)
        .subscribe(
            eq(ExecutionClientEventsChannel.class),
            any(ExecutionClientEngineApiCapabilitiesProvider.class));
  }

  @Test
  public void shouldFetchRemoteCapabilitiesWithinDesiredInterval() {
    createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    // Initial run
    asyncRunner.executeQueuedActions();
    verify(executionEngineClient, times(1)).exchangeCapabilities(any());

    // Fist scheduled run
    timeProvider.advanceTimeBySeconds(EXCHANGE_CAPABILITIES_FETCH_INTERVAL_IN_SECONDS);
    asyncRunner.executeQueuedActions();
    verify(executionEngineClient, times(2)).exchangeCapabilities(any());
  }

  @Test
  public void shouldNotFetchRemoteCapabilitiesUntilExecutionClientIsOnline() {
    final ExecutionClientEngineApiCapabilitiesProvider provider =
        new ExecutionClientEngineApiCapabilitiesProvider(
            asyncRunner, executionEngineClient, localCapabilitiesProvider, eventChannels);

    verifyNoInteractions(executionEngineClient);

    provider.onAvailabilityUpdated(true);
    asyncRunner.executeQueuedActions();

    verify(executionEngineClient, times(1)).exchangeCapabilities(any());
  }

  @Test
  public void shouldFetchRemoteCapabilitiesAfterExecutionClientReconnects() {
    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    asyncRunner.executeQueuedActions();
    verify(executionEngineClient, times(1)).exchangeCapabilities(any());

    remoteCapabilitiesProvider.onAvailabilityUpdated(false);
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(executionEngineClient);

    remoteCapabilitiesProvider.onAvailabilityUpdated(true);
    asyncRunner.executeQueuedActions();
    verify(executionEngineClient, times(2)).exchangeCapabilities(any());
  }

  @Test
  public void shouldNotBeAvailableUntilFetchTaskHasRun() {
    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    remoteCapabilitiesProvider.onAvailabilityUpdated(true);
    assertThat(remoteCapabilitiesProvider.isAvailable()).isFalse();

    asyncRunner.executeQueuedActions();
    assertThat(remoteCapabilitiesProvider.isAvailable()).isTrue();
  }

  @Test
  public void shouldNotBeAvailableIfRemoteAvailableMethodsIsEmpty() {
    final ExecutionClientEngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        createRemoteCapabilitiesProviderWithMatchingCapabilities(arbitraryListOfMethods());

    mockExecutionEngineClientForFailedCapabilitiesResponse("");
    remoteCapabilitiesProvider.onAvailabilityUpdated(true);

    asyncRunner.executeQueuedActions();
    verify(executionEngineClient).exchangeCapabilities(any());
    assertThat(remoteCapabilitiesProvider.supportedMethods()).isEmpty();
    assertThat(remoteCapabilitiesProvider.isAvailable()).isFalse();
  }

  private void mockExecutionEngineClientForCapabilities(
      final List<String> expectedRemoteSupportedMethods) {
    when(executionEngineClient.exchangeCapabilities(any()))
        .thenReturn(SafeFuture.completedFuture(new Response<>(expectedRemoteSupportedMethods)));
  }

  private void mockExecutionEngineClientForFailedCapabilitiesResponse(final String errorMessage) {
    when(executionEngineClient.exchangeCapabilities(any()))
        .thenReturn(SafeFuture.completedFuture(Response.withErrorMessage(errorMessage)));
  }

  private List<EngineJsonRpcMethod<?>> arbitraryListOfMethods() {
    final EngineJsonRpcMethod<?> method1 =
        new EngineGetPayloadV1(executionEngineClient, mock(Spec.class));
    final EngineJsonRpcMethod<?> method2 = new EngineNewPayloadV1(executionEngineClient);
    final EngineJsonRpcMethod<?> method3 = new EngineNewPayloadV2(executionEngineClient);
    return List.of(method1, method2, method3);
  }

  private List<String> asVersionedNames(final Collection<EngineJsonRpcMethod<?>> methods) {
    return methods.stream().map(EngineJsonRpcMethod::getVersionedName).collect(Collectors.toList());
  }
}
