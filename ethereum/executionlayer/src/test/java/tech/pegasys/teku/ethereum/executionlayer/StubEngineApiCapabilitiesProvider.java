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
import java.util.List;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;

public class StubEngineApiCapabilitiesProvider implements EngineApiCapabilitiesProvider {

  private final Collection<EngineJsonRpcMethod<?>> supportedMethods;
  private final boolean available;

  public StubEngineApiCapabilitiesProvider(
      final Collection<EngineJsonRpcMethod<?>> supportedMethods) {
    this.supportedMethods = supportedMethods;
    this.available = true;
  }

  private StubEngineApiCapabilitiesProvider(boolean available) {
    this.supportedMethods = List.of();
    this.available = available;
  }

  @Override
  public Collection<EngineJsonRpcMethod<?>> supportedMethods() {
    return supportedMethods;
  }

  @Override
  public boolean isAvailable() {
    return available;
  }

  public static StubEngineApiCapabilitiesProvider withMethods(EngineJsonRpcMethod<?>... methods) {
    return new StubEngineApiCapabilitiesProvider(List.of(methods));
  }

  public static StubEngineApiCapabilitiesProvider notAvailable() {
    return new StubEngineApiCapabilitiesProvider(false);
  }
}
