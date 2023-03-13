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
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethods;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineExchangeTransitionConfigurationV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetBlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.methods.EthGetBlockByHash;
import tech.pegasys.teku.ethereum.executionclient.methods.EthGetBlockByNumber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class MilestoneBasedExecutionJsonRpcMethodsResolverTest {

  private ExecutionEngineClient executionEngineClient;

  @BeforeEach
  public void setUp() {
    executionEngineClient = mock(ExecutionEngineClient.class);
  }

  @ParameterizedTest
  @MethodSource("bellatrixMethods")
  void shouldProvideExpectedMethodsForBellatrix(
      EngineApiMethods method, Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();

    final LocallySupportedEngineApiCapabilitiesProvider capabilitiesProvider =
        new LocallySupportedEngineApiCapabilitiesProvider(bellatrixSpec, executionEngineClient);
    final MilestoneBasedExecutionJsonRpcMethodsResolver methodsResolver =
        new MilestoneBasedExecutionJsonRpcMethodsResolver(bellatrixSpec, capabilitiesProvider);

    final EngineJsonRpcMethod<Object> providedMethod =
        methodsResolver.getMethod(method, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> bellatrixMethods() {
    return Stream.of(
        arguments(EngineApiMethods.ETH_GET_BLOCK_BY_HASH, EthGetBlockByHash.class),
        arguments(EngineApiMethods.ETH_GET_BLOCK_BY_NUMBER, EthGetBlockByNumber.class),
        arguments(EngineApiMethods.ENGINE_NEW_PAYLOAD, EngineNewPayloadV1.class),
        arguments(EngineApiMethods.ENGINE_GET_PAYLOAD, EngineGetPayloadV1.class),
        arguments(EngineApiMethods.ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV1.class),
        arguments(
            EngineApiMethods.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION,
            EngineExchangeTransitionConfigurationV1.class));
  }

  @ParameterizedTest
  @MethodSource("capellaMethods")
  void shouldProvideExpectedMethodsForCapella(
      EngineApiMethods method, Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();

    final LocallySupportedEngineApiCapabilitiesProvider capabilitiesProvider =
        new LocallySupportedEngineApiCapabilitiesProvider(capellaSpec, executionEngineClient);
    final MilestoneBasedExecutionJsonRpcMethodsResolver methodsResolver =
        new MilestoneBasedExecutionJsonRpcMethodsResolver(capellaSpec, capabilitiesProvider);

    final EngineJsonRpcMethod<Object> providedMethod =
        methodsResolver.getMethod(method, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> capellaMethods() {
    return Stream.of(
        arguments(EngineApiMethods.ETH_GET_BLOCK_BY_HASH, EthGetBlockByHash.class),
        arguments(EngineApiMethods.ETH_GET_BLOCK_BY_NUMBER, EthGetBlockByNumber.class),
        arguments(EngineApiMethods.ENGINE_NEW_PAYLOAD, EngineNewPayloadV2.class),
        arguments(EngineApiMethods.ENGINE_GET_PAYLOAD, EngineGetPayloadV2.class),
        arguments(EngineApiMethods.ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV2.class),
        arguments(
            EngineApiMethods.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION,
            EngineExchangeTransitionConfigurationV1.class));
  }

  @ParameterizedTest
  @MethodSource("denebMethods")
  void shouldProvideExpectedMethodsForDeneb(
      EngineApiMethods method, Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec denebSpec = TestSpecFactory.createMinimalDeneb();

    final LocallySupportedEngineApiCapabilitiesProvider capabilitiesProvider =
        new LocallySupportedEngineApiCapabilitiesProvider(denebSpec, executionEngineClient);
    final MilestoneBasedExecutionJsonRpcMethodsResolver methodsResolver =
        new MilestoneBasedExecutionJsonRpcMethodsResolver(denebSpec, capabilitiesProvider);

    final EngineJsonRpcMethod<Object> providedMethod =
        methodsResolver.getMethod(method, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> denebMethods() {
    return Stream.of(
        arguments(EngineApiMethods.ETH_GET_BLOCK_BY_HASH, EthGetBlockByHash.class),
        arguments(EngineApiMethods.ETH_GET_BLOCK_BY_NUMBER, EthGetBlockByNumber.class),
        arguments(EngineApiMethods.ENGINE_NEW_PAYLOAD, EngineNewPayloadV3.class),
        arguments(EngineApiMethods.ENGINE_GET_PAYLOAD, EngineGetPayloadV3.class),
        arguments(EngineApiMethods.ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV2.class),
        arguments(
            EngineApiMethods.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION,
            EngineExchangeTransitionConfigurationV1.class),
        arguments(EngineApiMethods.ENGINE_GET_BLOBS_BUNDLE, EngineGetBlobsBundleV1.class));
  }
}
