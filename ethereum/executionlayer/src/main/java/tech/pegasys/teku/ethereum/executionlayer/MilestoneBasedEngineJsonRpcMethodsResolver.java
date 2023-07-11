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

import static tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod.ENGINE_FORK_CHOICE_UPDATED;
import static tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod.ENGINE_GET_PAYLOAD;
import static tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod.ENGINE_NEW_PAYLOAD;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV3;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineGetPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV3;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;

public class MilestoneBasedEngineJsonRpcMethodsResolver implements EngineJsonRpcMethodsResolver {

  private final Map<SpecMilestone, Map<EngineApiMethod, EngineJsonRpcMethod<?>>>
      methodsByMilestone = new HashMap<>();

  private final Spec spec;
  private final ExecutionEngineClient executionEngineClient;

  public MilestoneBasedEngineJsonRpcMethodsResolver(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    this.spec = spec;
    this.executionEngineClient = executionEngineClient;

    // Milestone specific methods
    spec.getEnabledMilestones().stream()
        .map(ForkAndSpecMilestone::getSpecMilestone)
        .forEach(
            milestone -> {
              switch (milestone) {
                case PHASE0:
                case ALTAIR:
                  break;
                case BELLATRIX:
                  methodsByMilestone.put(milestone, bellatrixSupportedMethods());
                  break;
                case CAPELLA:
                  methodsByMilestone.put(milestone, capellaSupportedMethods());
                  break;
                case DENEB:
                  methodsByMilestone.put(milestone, denebSupportedMethods());
                  break;
              }
            });
  }

  private Map<EngineApiMethod, EngineJsonRpcMethod<?>> bellatrixSupportedMethods() {
    final Map<EngineApiMethod, EngineJsonRpcMethod<?>> methods = new HashMap<>();

    methods.put(ENGINE_NEW_PAYLOAD, new EngineNewPayloadV1(executionEngineClient));
    methods.put(ENGINE_GET_PAYLOAD, new EngineGetPayloadV1(executionEngineClient, spec));
    methods.put(ENGINE_FORK_CHOICE_UPDATED, new EngineForkChoiceUpdatedV1(executionEngineClient));

    return methods;
  }

  private Map<EngineApiMethod, EngineJsonRpcMethod<?>> capellaSupportedMethods() {
    final Map<EngineApiMethod, EngineJsonRpcMethod<?>> methods = new HashMap<>();

    methods.put(ENGINE_NEW_PAYLOAD, new EngineNewPayloadV2(executionEngineClient));
    methods.put(ENGINE_GET_PAYLOAD, new EngineGetPayloadV2(executionEngineClient, spec));
    methods.put(ENGINE_FORK_CHOICE_UPDATED, new EngineForkChoiceUpdatedV2(executionEngineClient));

    return methods;
  }

  private Map<EngineApiMethod, EngineJsonRpcMethod<?>> denebSupportedMethods() {
    final Map<EngineApiMethod, EngineJsonRpcMethod<?>> methods = new HashMap<>();

    methods.put(ENGINE_NEW_PAYLOAD, new EngineNewPayloadV3(executionEngineClient));
    methods.put(ENGINE_GET_PAYLOAD, new EngineGetPayloadV3(executionEngineClient, spec));
    methods.put(ENGINE_FORK_CHOICE_UPDATED, new EngineForkChoiceUpdatedV3(executionEngineClient));

    return methods;
  }

  @Override
  @SuppressWarnings({"unchecked", "unused"})
  public <T> EngineJsonRpcMethod<T> getMethod(
      final EngineApiMethod method,
      final Supplier<SpecMilestone> milestoneSupplier,
      final Class<T> resultType) {
    final SpecMilestone milestone = milestoneSupplier.get();
    final Map<EngineApiMethod, EngineJsonRpcMethod<?>> milestoneMethods =
        methodsByMilestone.getOrDefault(milestone, Collections.emptyMap());
    final EngineJsonRpcMethod<T> foundMethod =
        (EngineJsonRpcMethod<T>) milestoneMethods.get(method);
    if (foundMethod == null) {
      throw new IllegalArgumentException(
          "Can't find method with name " + method.getName() + " for milestone " + milestone);
    }
    return foundMethod;
  }
}
