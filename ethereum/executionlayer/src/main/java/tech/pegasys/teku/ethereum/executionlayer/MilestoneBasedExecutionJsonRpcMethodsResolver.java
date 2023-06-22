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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethods;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

public class MilestoneBasedExecutionJsonRpcMethodsResolver
    implements ExecutionJsonRpcMethodsResolver {

  @SuppressWarnings("rawtypes")
  private final List<EngineJsonRpcMethod> methods = new ArrayList<>();

  @SuppressWarnings("rawtypes")
  private final Map<SpecMilestone, List<EngineJsonRpcMethod>> milestoneMethods = new HashMap<>();

  private final Spec spec;

  public MilestoneBasedExecutionJsonRpcMethodsResolver(
      final Spec spec, final EngineApiCapabilitiesProvider capabilitiesProvider) {
    this.spec = spec;
    final Collection<String> supportedMethods = new HashSet<>();
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      supportedMethods.addAll(bellatrixSupportedMethods());
    }

    if (spec.isMilestoneSupported(SpecMilestone.CAPELLA)) {
      supportedMethods.addAll(capellaSupportedMethods());
    }

    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      supportedMethods.addAll(denebSupportedMethods());
    }

    capabilitiesProvider.supportedMethods().stream()
        .filter(method -> supportedMethods.contains(method.getVersionedName()))
        .forEach(
            method ->
                method
                    .getApplicableMilestone()
                    .ifPresentOrElse(
                        milestone ->
                            milestoneMethods
                                .computeIfAbsent(milestone, __ -> new ArrayList<>())
                                .add(method),
                        () -> methods.add(method)));
  }

  private static Collection<String> bellatrixSupportedMethods() {
    final Collection<String> methods = new HashSet<>();

    methods.add(EngineApiMethods.ETH_GET_BLOCK_BY_HASH.getName());
    methods.add(EngineApiMethods.ETH_GET_BLOCK_BY_NUMBER.getName());
    methods.add(EngineApiMethods.ENGINE_NEW_PAYLOAD.getName() + "V1");
    methods.add(EngineApiMethods.ENGINE_GET_PAYLOAD.getName() + "V1");
    methods.add(EngineApiMethods.ENGINE_FORK_CHOICE_UPDATED.getName() + "V1");
    methods.add(EngineApiMethods.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION.getName() + "V1");

    return methods;
  }

  private static Collection<String> capellaSupportedMethods() {
    final Collection<String> methods = bellatrixSupportedMethods();

    methods.add(EngineApiMethods.ENGINE_NEW_PAYLOAD.getName() + "V2");
    methods.add(EngineApiMethods.ENGINE_GET_PAYLOAD.getName() + "V2");
    methods.add(EngineApiMethods.ENGINE_FORK_CHOICE_UPDATED.getName() + "V2");

    return methods;
  }

  private static Collection<String> denebSupportedMethods() {
    final Collection<String> methods = capellaSupportedMethods();

    methods.add(EngineApiMethods.ENGINE_NEW_PAYLOAD.getName() + "V3");
    methods.add(EngineApiMethods.ENGINE_GET_PAYLOAD.getName() + "V3");

    return methods;
  }

  @Override
  public <T> EngineJsonRpcMethod<T> getMethod(
      final EngineApiMethods method, final Class<T> resultType) {
    return findMethod(methods, method);
  }

  @Override
  public <T> EngineJsonRpcMethod<T> getMilestoneMethod(
      final EngineApiMethods method,
      final Function<Spec, SpecMilestone> milestoneResolver,
      final Class<T> resultType) {
    final SpecMilestone milestone = milestoneResolver.apply(spec);
    return findMethod(milestoneMethods.get(milestone), method);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private <T> EngineJsonRpcMethod<T> findMethod(
      final List<EngineJsonRpcMethod> methods, final EngineApiMethods method) {
    return methods.stream()
        .filter(m -> m.getName().equals(method.getName()))
        .max(Comparator.comparingInt(EngineJsonRpcMethod::getVersion))
        .orElseThrow(
            () -> new IllegalArgumentException("Can't find method with name " + method.getName()));
  }
}
