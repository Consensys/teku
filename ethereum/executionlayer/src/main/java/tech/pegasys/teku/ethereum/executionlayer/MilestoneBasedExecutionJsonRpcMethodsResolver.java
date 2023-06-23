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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineExchangeTransitionConfigurationV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV2;
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
import tech.pegasys.teku.spec.SpecMilestone;

public class MilestoneBasedExecutionJsonRpcMethodsResolver
    implements ExecutionJsonRpcMethodsResolver {

  private final List<EngineJsonRpcMethod<?>> nonMilestoneMethods = new ArrayList<>();
  private final Map<SpecMilestone, List<EngineJsonRpcMethod<?>>> milestoneMethods = new HashMap<>();

  private final Spec spec;
  private final ExecutionEngineClient executionEngineClient;

  public MilestoneBasedExecutionJsonRpcMethodsResolver(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    this.spec = spec;
    this.executionEngineClient = executionEngineClient;

    // Non-milestone specific methods
    nonMilestoneMethods.add(new EthGetBlockByHash(executionEngineClient));
    nonMilestoneMethods.add(new EthGetBlockByNumber(executionEngineClient));
    nonMilestoneMethods.add(new EngineExchangeTransitionConfigurationV1(executionEngineClient));

    // Milestone specific methods
    milestoneMethods.put(SpecMilestone.BELLATRIX, bellatrixSupportedMethods());
    milestoneMethods.put(SpecMilestone.CAPELLA, capellaSupportedMethods());
    milestoneMethods.put(SpecMilestone.DENEB, denebSupportedMethods());
  }

  private List<EngineJsonRpcMethod<?>> bellatrixSupportedMethods() {
    final List<EngineJsonRpcMethod<?>> methods = new ArrayList<>();

    methods.add(new EngineNewPayloadV1(executionEngineClient));
    methods.add(new EngineGetPayloadV1(executionEngineClient, spec));
    methods.add(new EngineForkChoiceUpdatedV1(executionEngineClient));

    return methods;
  }

  private List<EngineJsonRpcMethod<?>> capellaSupportedMethods() {
    final List<EngineJsonRpcMethod<?>> methods = new ArrayList<>();

    methods.add(new EngineNewPayloadV2(executionEngineClient));
    methods.add(new EngineGetPayloadV2(executionEngineClient, spec));
    methods.add(new EngineForkChoiceUpdatedV2(executionEngineClient, spec));

    return methods;
  }

  private List<EngineJsonRpcMethod<?>> denebSupportedMethods() {
    final List<EngineJsonRpcMethod<?>> methods = new ArrayList<>();

    methods.add(new EngineNewPayloadV3(executionEngineClient));
    methods.add(new EngineGetPayloadV3(executionEngineClient, spec));
    methods.add(new EngineForkChoiceUpdatedV2(executionEngineClient, spec));

    return methods;
  }

  @Override
  public <T> EngineJsonRpcMethod<T> getMethod(
      final EngineApiMethod method, final Class<T> resultType) {
    return findMethod(nonMilestoneMethods, method, resultType)
        .orElseThrow(
            () -> new IllegalArgumentException("Can't find method with name " + method.getName()));
  }

  @Override
  public <T> EngineJsonRpcMethod<T> getMilestoneMethod(
      final EngineApiMethod method,
      final Supplier<SpecMilestone> milestoneSupplier,
      final Class<T> resultType) {
    final SpecMilestone milestone = milestoneSupplier.get();
    return findMethod(milestoneMethods.get(milestone), method, resultType)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Can't find method with name " + method.getName() + " for " + milestone));
  }

  @SuppressWarnings({"unchecked", "unused"})
  private <T> Optional<EngineJsonRpcMethod<T>> findMethod(
      final List<EngineJsonRpcMethod<?>> methods,
      final EngineApiMethod method,
      final Class<T> resultType) {
    return methods.stream()
        .filter(m -> m.getName().equals(method.getName()))
        .findFirst()
        .map(m -> (EngineJsonRpcMethod<T>) m);
  }
}
