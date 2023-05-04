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

package tech.pegasys.teku.ethereum.executionclient.methods;

public enum EngineApiMethods {
  ETH_GET_BLOCK_BY_HASH("eth_getBlockByHash"),
  ETH_GET_BLOCK_BY_NUMBER("eth_getBlockByNumber"),
  ENGINE_NEW_PAYLOAD("engine_newPayload"),
  ENGINE_GET_PAYLOAD("engine_getPayload"),
  ENGINE_FORK_CHOICE_UPDATED("engine_forkchoiceUpdated"),
  ENGINE_EXCHANGE_TRANSITION_CONFIGURATION("engine_exchangeTransitionConfiguration");

  private final String name;

  EngineApiMethods(final String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
