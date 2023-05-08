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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
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

public class LocalEngineApiCapabilitiesProvider implements EngineApiCapabilitiesProvider {

  private final Collection<EngineJsonRpcMethod<?>> supportedMethods = new HashSet<>();

  public LocalEngineApiCapabilitiesProvider(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    // Eth1 methods
    supportedMethods.add(new EthGetBlockByHash(executionEngineClient));
    supportedMethods.add(new EthGetBlockByNumber(executionEngineClient));

    // New Payload
    supportedMethods.add(new EngineNewPayloadV1(executionEngineClient));
    supportedMethods.add(new EngineNewPayloadV2(executionEngineClient));
    supportedMethods.add(new EngineNewPayloadV3(executionEngineClient));

    // Get Payload
    supportedMethods.add(new EngineGetPayloadV1(executionEngineClient, spec));
    supportedMethods.add(new EngineGetPayloadV2(executionEngineClient, spec));
    supportedMethods.add(new EngineGetPayloadV3(executionEngineClient, spec));

    // Fork Choice Updated
    supportedMethods.add(new EngineForkChoiceUpdatedV1(executionEngineClient));
    supportedMethods.add(new EngineForkChoiceUpdatedV2(executionEngineClient, spec));

    // Exchange Transition Configuration
    supportedMethods.add(new EngineExchangeTransitionConfigurationV1(executionEngineClient));
  }

  @Override
  public Collection<EngineJsonRpcMethod<?>> supportedMethods() {
    return Collections.unmodifiableCollection(supportedMethods);
  }

  @Override
  public boolean isAvailable() {
    return true;
  }
}
