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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethods;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;

public class NegotiatedExecutionJsonRpcMethodsResolver implements ExecutionJsonRpcMethodsResolver {

  private static final Logger LOG = LogManager.getLogger();

  private final EngineApiCapabilitiesProvider localEngineApiCapabilitiesProvider;
  private final EngineApiCapabilitiesProvider remoteEngineApiCapabilitiesProvider;

  private final ExecutionJsonRpcMethodsResolver fallbackResolver;

  public NegotiatedExecutionJsonRpcMethodsResolver(
      final EngineApiCapabilitiesProvider localEngineApiCapabilitiesProvider,
      final EngineApiCapabilitiesProvider remoteEngineApiCapabilitiesProvider,
      final ExecutionJsonRpcMethodsResolver fallbackResolver) {
    this.localEngineApiCapabilitiesProvider = localEngineApiCapabilitiesProvider;
    this.remoteEngineApiCapabilitiesProvider = remoteEngineApiCapabilitiesProvider;
    this.fallbackResolver = fallbackResolver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> EngineJsonRpcMethod<T> getMethod(
      final EngineApiMethods method, final Class<T> resultType) {
    if (!remoteEngineApiCapabilitiesProvider.isAvailable()) {
      return fallbackResolver.getMethod(method, resultType);
    }

    final List<EngineJsonRpcMethod<?>> availableLocalMethodVersions =
        getAvailableMethodVersions(method, localEngineApiCapabilitiesProvider.supportedMethods());
    if (shouldSkipNegotiation(availableLocalMethodVersions)) {
      return (EngineJsonRpcMethod<T>) availableLocalMethodVersions.get(0);
    }

    final List<EngineJsonRpcMethod<?>> availableRemoteMethodVersions =
        getAvailableMethodVersions(method, remoteEngineApiCapabilitiesProvider.supportedMethods());

    final EngineJsonRpcMethod<T> negotiatedMethod =
        negotiateMethod(availableLocalMethodVersions, availableRemoteMethodVersions);

    if (negotiatedMethod == null) {
      final String errorMessage =
          detailedErrorMessage(method, availableLocalMethodVersions, availableRemoteMethodVersions);
      LOG.warn(errorMessage);
      throw new ExchangeCapabilitiesException(errorMessage);
    }

    return negotiatedMethod;
  }

  private boolean shouldSkipNegotiation(
      final List<EngineJsonRpcMethod<?>> availableLocalMethodVersions) {
    return availableLocalMethodVersions.size() == 1
        && !availableLocalMethodVersions.get(0).isNegotiable();
  }

  private List<EngineJsonRpcMethod<?>> getAvailableMethodVersions(
      final EngineApiMethods method, final Collection<EngineJsonRpcMethod<?>> availableMethods) {
    return availableMethods.stream()
        .filter(m -> m.getName().equals(method.getName()))
        .sorted(Comparator.comparingInt(EngineJsonRpcMethod::getVersion))
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private <T> EngineJsonRpcMethod<T> negotiateMethod(
      final List<EngineJsonRpcMethod<?>> localMethodVersions,
      final List<EngineJsonRpcMethod<?>> remoteMethodVersions) {
    int localIndex = localMethodVersions.size() - 1;
    int remoteIndex = remoteMethodVersions.size() - 1;

    while (localIndex >= 0 && remoteIndex >= 0) {
      final EngineJsonRpcMethod<?> localMethod = localMethodVersions.get(localIndex);
      final EngineJsonRpcMethod<?> remoteMethod = remoteMethodVersions.get(remoteIndex);
      if (localMethod.getVersion() == remoteMethod.getVersion()) {
        return (EngineJsonRpcMethod<T>) localMethod;
      } else if (localMethod.getVersion() > remoteMethod.getVersion()) {
        localIndex--;
      } else {
        remoteIndex--;
      }
    }

    return null;
  }

  private String detailedErrorMessage(
      final EngineApiMethods method,
      final List<EngineJsonRpcMethod<?>> availableLocalMethodVersions,
      final List<EngineJsonRpcMethod<?>> availableRemoteMethodVersions) {
    return String.format(
        "No matching version between Consensus and Execution client for Engine API method %s. CL available "
            + "versions = %s, EL available versions = %s",
        method.getName(),
        availableLocalMethodVersions.stream()
            .map(EngineJsonRpcMethod::getVersionedName)
            .collect(Collectors.toList()),
        availableRemoteMethodVersions.stream()
            .map(EngineJsonRpcMethod::getVersionedName)
            .collect(Collectors.toList()));
  }
}
