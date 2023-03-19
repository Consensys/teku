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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethods;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV2;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

@TestInstance(Lifecycle.PER_CLASS)
class NegotiatedExecutionJsonRpcMethodsResolverTest {

  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);

  private final ExecutionJsonRpcMethodsResolver fallbackResolver =
      mock(ExecutionJsonRpcMethodsResolver.class);

  @ParameterizedTest(name = "{0}")
  @MethodSource("capabilitiesArgumentsSource")
  public void testExchangeCapabilitiesPossibleNegotiations(
      String description,
      List<EngineJsonRpcMethod<?>> localSupportedMethods,
      List<EngineJsonRpcMethod<?>> remoteSupportedMethods,
      Class<? extends EngineJsonRpcMethod<?>> expectedMethodClass) {

    final EngineApiCapabilitiesProvider localCapabilitiesProvider =
        stubCapabilitiesProvider(localSupportedMethods);
    final EngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        stubCapabilitiesProvider(remoteSupportedMethods);

    final NegotiatedExecutionJsonRpcMethodsResolver methodProvider =
        new NegotiatedExecutionJsonRpcMethodsResolver(
            localCapabilitiesProvider, remoteCapabilitiesProvider, fallbackResolver);

    final EngineJsonRpcMethod<PayloadStatus> providedMethod =
        methodProvider.getMethod(EngineApiMethods.ENGINE_NEW_PAYLOAD, PayloadStatus.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private Stream<Arguments> capabilitiesArgumentsSource() {
    final EngineJsonRpcMethod<?> methodV1 = new EngineNewPayloadV1(executionEngineClient);
    final EngineJsonRpcMethod<?> methodV2 = new EngineNewPayloadV2(executionEngineClient);

    return Stream.of(
        Arguments.of(
            "Local and Remote support older and newer version of method",
            List.of(methodV1, methodV2),
            List.of(methodV1, methodV2),
            EngineNewPayloadV2.class),
        Arguments.of(
            "Local and Remote support only newer version of method",
            List.of(methodV2),
            List.of(methodV2),
            EngineNewPayloadV2.class),
        Arguments.of(
            "Local and Remote support only older version of method",
            List.of(methodV1),
            List.of(methodV1),
            EngineNewPayloadV1.class),
        Arguments.of(
            "Local only supports older version of method, Remote supports both",
            List.of(methodV1),
            List.of(methodV1, methodV2),
            EngineNewPayloadV1.class),
        Arguments.of(
            "Remote only supports older version of method, Local supports both",
            List.of(methodV1, methodV2),
            List.of(methodV1),
            EngineNewPayloadV1.class),
        Arguments.of(
            "Local only supports newer version of method, Remote supports both",
            List.of(methodV2),
            List.of(methodV1, methodV2),
            EngineNewPayloadV2.class),
        Arguments.of(
            "Remote only supports newer version of method, Local supports both",
            List.of(methodV1, methodV2),
            List.of(methodV2),
            EngineNewPayloadV2.class));
  }

  @Test
  public void testExchangeCapabilitiesWithoutMatchingMethods() {
    final EngineJsonRpcMethod<?> methodV1 = new EngineNewPayloadV1(executionEngineClient);
    final EngineJsonRpcMethod<?> methodV2 = new EngineNewPayloadV2(executionEngineClient);

    final EngineApiCapabilitiesProvider localCapabilitiesProvider =
        stubCapabilitiesProvider(List.of(methodV1));
    final EngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        stubCapabilitiesProvider(List.of(methodV2));

    final NegotiatedExecutionJsonRpcMethodsResolver methodProvider =
        new NegotiatedExecutionJsonRpcMethodsResolver(
            localCapabilitiesProvider, remoteCapabilitiesProvider, fallbackResolver);

    assertThatThrownBy(
            () ->
                methodProvider.getMethod(EngineApiMethods.ENGINE_NEW_PAYLOAD, PayloadStatus.class))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldNotUseFallbackMethodResolverWhenRemoteCapabilitiesProviderIsAvailable() {
    final EngineNewPayloadV1 method = new EngineNewPayloadV1(executionEngineClient);
    final EngineApiCapabilitiesProvider localCapabilitiesProvider =
        StubEngineApiCapabilitiesProvider.withMethods(method);
    final EngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        StubEngineApiCapabilitiesProvider.withMethods(method);

    final NegotiatedExecutionJsonRpcMethodsResolver methodProvider =
        new NegotiatedExecutionJsonRpcMethodsResolver(
            localCapabilitiesProvider, remoteCapabilitiesProvider, fallbackResolver);

    methodProvider.getMethod(EngineApiMethods.ENGINE_NEW_PAYLOAD, PayloadStatus.class);

    verifyNoInteractions(fallbackResolver);
  }

  @Test
  public void shouldUseFallbackMethodResolverWhenRemoteCapabilitiesProviderIsNotAvailable() {
    final EngineApiCapabilitiesProvider localCapabilitiesProvider =
        StubEngineApiCapabilitiesProvider.withMethods();
    final EngineApiCapabilitiesProvider remoteCapabilitiesProvider =
        StubEngineApiCapabilitiesProvider.notAvailable();

    when(fallbackResolver.getMethod(
            eq(EngineApiMethods.ENGINE_NEW_PAYLOAD), eq(PayloadStatus.class)))
        .thenReturn(new EngineNewPayloadV1(executionEngineClient));

    final NegotiatedExecutionJsonRpcMethodsResolver methodProvider =
        new NegotiatedExecutionJsonRpcMethodsResolver(
            localCapabilitiesProvider, remoteCapabilitiesProvider, fallbackResolver);

    methodProvider.getMethod(EngineApiMethods.ENGINE_NEW_PAYLOAD, PayloadStatus.class);

    verify(fallbackResolver, times(1)).getMethod(any(), any());
  }

  private EngineApiCapabilitiesProvider stubCapabilitiesProvider(
      Collection<EngineJsonRpcMethod<?>> methods) {
    return new StubEngineApiCapabilitiesProvider(methods);
  }
}
