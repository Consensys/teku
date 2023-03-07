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
import java.util.Comparator;
import java.util.List;
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
import tech.pegasys.teku.spec.SpecMilestone;

public class MilestoneBasedExecutionJsonRpcMethodsProvider
    implements ExecutionJsonRpcMethodsProvider {

  @SuppressWarnings("rawtypes")
  private final List<EngineJsonRpcMethod> methods = new ArrayList<>();

  public MilestoneBasedExecutionJsonRpcMethodsProvider(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      methods.add(new EthGetBlockByHash(executionEngineClient));
      methods.add(new EthGetBlockByNumber(executionEngineClient));
      methods.add(new EngineNewPayloadV1(executionEngineClient));
      methods.add(new EngineGetPayloadV1(executionEngineClient, spec));
      methods.add(new EngineForkChoiceUpdatedV1(executionEngineClient));
      methods.add(new EngineExchangeTransitionConfigurationV1(executionEngineClient));
    }

    if (spec.isMilestoneSupported(SpecMilestone.CAPELLA)) {
      methods.add(new EngineNewPayloadV2(executionEngineClient));
      methods.add(new EngineGetPayloadV2(executionEngineClient, spec));
      methods.add(new EngineForkChoiceUpdatedV2(executionEngineClient, spec));
    }

    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      methods.add(new EngineNewPayloadV3(executionEngineClient));
      methods.add(new EngineGetPayloadV3(executionEngineClient, spec));
      methods.add(new EngineGetBlobsBundleV1(executionEngineClient, spec));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> EngineJsonRpcMethod<T> getMethod(
      final EngineApiMethods method, final Class<T> resultType) {
    return methods.stream()
        .filter(m -> m.getName().equals(method.getName()))
        .max(Comparator.comparingInt(EngineJsonRpcMethod::getVersion))
        .orElseThrow(
            () -> new IllegalArgumentException("Can't find method with name " + method.getName()));
  }
}
