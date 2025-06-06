/*
 * Copyright Consensys Software Inc., 2025
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
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod.ENGINE_FORK_CHOICE_UPDATED;
import static tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod.ENGINE_GET_PAYLOAD;
import static tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod.ENGINE_NEW_PAYLOAD;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV3;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV4;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV5;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

class MilestoneBasedEngineJsonRpcMethodsResolverTest {

  private ExecutionEngineClient executionEngineClient;

  @BeforeEach
  public void setUp() {
    executionEngineClient = mock(ExecutionEngineClient.class);
  }

  @Test
  void bellatrixMilestoneMethodIsNotSupportedInAltair() {
    final Spec altairSpec = TestSpecFactory.createMinimalAltair();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(altairSpec, executionEngineClient);

    assertThatThrownBy(
            () ->
                engineMethodsResolver.getMethod(
                    ENGINE_GET_PAYLOAD, () -> SpecMilestone.BELLATRIX, Object.class))
        .hasMessage("Can't find method with name engine_getPayload for milestone BELLATRIX");
  }

  @ParameterizedTest
  @MethodSource("bellatrixMethods")
  void shouldProvideExpectedMethodsForBellatrix(
      final EngineApiMethod method, final Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(bellatrixSpec, executionEngineClient);

    final EngineJsonRpcMethod<Object> providedMethod =
        engineMethodsResolver.getMethod(method, () -> SpecMilestone.BELLATRIX, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> bellatrixMethods() {
    return Stream.of(
        arguments(ENGINE_NEW_PAYLOAD, EngineNewPayloadV1.class),
        arguments(ENGINE_GET_PAYLOAD, EngineGetPayloadV1.class),
        arguments(ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV1.class));
  }

  @Test
  void capellaMilestoneMethodIsNotSupportedInBellatrix() {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(bellatrixSpec, executionEngineClient);

    assertThatThrownBy(
            () ->
                engineMethodsResolver.getMethod(
                    ENGINE_GET_PAYLOAD, () -> SpecMilestone.CAPELLA, Object.class))
        .hasMessage("Can't find method with name engine_getPayload for milestone CAPELLA");
  }

  @ParameterizedTest
  @MethodSource("capellaMethods")
  void shouldProvideExpectedMethodsForCapella(
      final EngineApiMethod method, final Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(capellaSpec, executionEngineClient);

    final EngineJsonRpcMethod<Object> providedMethod =
        engineMethodsResolver.getMethod(method, () -> SpecMilestone.CAPELLA, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> capellaMethods() {
    return Stream.of(
        arguments(ENGINE_NEW_PAYLOAD, EngineNewPayloadV2.class),
        arguments(ENGINE_GET_PAYLOAD, EngineGetPayloadV2.class),
        arguments(ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV2.class));
  }

  @Test
  void denebMilestoneMethodIsNotSupportedInCapella() {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(capellaSpec, executionEngineClient);

    assertThatThrownBy(
            () ->
                engineMethodsResolver.getMethod(
                    ENGINE_GET_PAYLOAD, () -> SpecMilestone.DENEB, Object.class))
        .hasMessage("Can't find method with name engine_getPayload for milestone DENEB");
  }

  @ParameterizedTest
  @MethodSource("denebMethods")
  void shouldProvideExpectedMethodsForDeneb(
      final EngineApiMethod method, final Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec denebSpec = TestSpecFactory.createMinimalDeneb();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(denebSpec, executionEngineClient);

    final EngineJsonRpcMethod<Object> providedMethod =
        engineMethodsResolver.getMethod(method, () -> SpecMilestone.DENEB, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> denebMethods() {
    return Stream.of(
        arguments(ENGINE_NEW_PAYLOAD, EngineNewPayloadV3.class),
        arguments(ENGINE_GET_PAYLOAD, EngineGetPayloadV3.class),
        arguments(ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV3.class));
  }

  @Test
  void electraMilestoneMethodIsNotSupportedInDeneb() {
    final Spec denebSpec = TestSpecFactory.createMinimalDeneb();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(denebSpec, executionEngineClient);

    assertThatThrownBy(
            () ->
                engineMethodsResolver.getMethod(
                    ENGINE_GET_PAYLOAD, () -> SpecMilestone.ELECTRA, Object.class))
        .hasMessage("Can't find method with name engine_getPayload for milestone ELECTRA");
  }

  @ParameterizedTest
  @MethodSource("electraMethods")
  void shouldProvideExpectedMethodsForElectra(
      final EngineApiMethod method, final Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec electraSpec = TestSpecFactory.createMinimalElectra();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(electraSpec, executionEngineClient);

    final EngineJsonRpcMethod<Object> providedMethod =
        engineMethodsResolver.getMethod(method, () -> SpecMilestone.ELECTRA, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> electraMethods() {
    return Stream.of(
        arguments(ENGINE_NEW_PAYLOAD, EngineNewPayloadV4.class),
        arguments(ENGINE_GET_PAYLOAD, EngineGetPayloadV4.class),
        arguments(ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV3.class));
  }

  @ParameterizedTest
  @MethodSource("fuluMethods")
  void shouldProvideExpectedMethodsForFulu(
      final EngineApiMethod method, final Class<EngineJsonRpcMethod<?>> expectedMethodClass) {
    final Spec fuluSpec = TestSpecFactory.createMinimalFulu();

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(fuluSpec, executionEngineClient);

    final EngineJsonRpcMethod<Object> providedMethod =
        engineMethodsResolver.getMethod(method, () -> SpecMilestone.FULU, Object.class);

    assertThat(providedMethod).isExactlyInstanceOf(expectedMethodClass);
  }

  private static Stream<Arguments> fuluMethods() {
    return Stream.of(
        arguments(ENGINE_NEW_PAYLOAD, EngineNewPayloadV4.class),
        arguments(ENGINE_GET_PAYLOAD, EngineGetPayloadV5.class),
        arguments(ENGINE_FORK_CHOICE_UPDATED, EngineForkChoiceUpdatedV3.class));
  }

  @Test
  void getsCapabilities() {
    final Spec spec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), UInt64.valueOf(4));

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(spec, executionEngineClient);

    final Set<String> capabilities = engineMethodsResolver.getCapabilities();

    assertThat(capabilities)
        .containsExactlyInAnyOrder(
            "engine_newPayloadV1",
            "engine_getPayloadV1",
            "engine_forkchoiceUpdatedV1",
            "engine_newPayloadV2",
            "engine_getPayloadV2",
            "engine_forkchoiceUpdatedV2",
            "engine_newPayloadV3",
            "engine_getPayloadV3",
            "engine_forkchoiceUpdatedV3",
            "engine_newPayloadV4",
            "engine_getPayloadV4",
            "engine_getPayloadV5");
  }
}
