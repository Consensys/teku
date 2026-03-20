/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.dataproviders.lookup;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

@FunctionalInterface
public interface ExecutionPayloadProvider {

  ExecutionPayloadProvider NOOP = roots -> SafeFuture.completedFuture(Collections.emptyMap());

  static ExecutionPayloadProvider fromDynamicMap(
      final Map<Bytes32, SignedExecutionPayloadEnvelope> payloadMap) {
    return roots ->
        SafeFuture.completedFuture(
            roots.stream()
                .flatMap(root -> Optional.ofNullable(payloadMap.get(root)).stream())
                .collect(
                    Collectors.toMap(
                        SignedExecutionPayloadEnvelope::getBeaconBlockRoot, Function.identity())));
  }

  /**
   * Combines multiple providers, querying the primary first and falling back to secondary providers
   * for any missing roots. Use this to combine a hot store provider with a database-backed
   * provider.
   */
  static ExecutionPayloadProvider combined(
      final ExecutionPayloadProvider primaryProvider,
      final ExecutionPayloadProvider... secondaryProviders) {
    return (final Set<Bytes32> blockRoots) -> {
      SafeFuture<Map<Bytes32, SignedExecutionPayloadEnvelope>> result =
          primaryProvider.getExecutionPayloads(blockRoots).thenApply(HashMap::new);
      for (ExecutionPayloadProvider nextProvider : secondaryProviders) {
        result =
            result.thenCompose(
                payloads -> {
                  final Set<Bytes32> remainingRoots =
                      Sets.difference(blockRoots, payloads.keySet());
                  if (remainingRoots.isEmpty()) {
                    return SafeFuture.completedFuture(payloads);
                  }
                  return nextProvider
                      .getExecutionPayloads(remainingRoots)
                      .thenApply(
                          morePayloads -> {
                            payloads.putAll(morePayloads);
                            return payloads;
                          });
                });
      }
      return result;
    };
  }

  SafeFuture<Map<Bytes32, SignedExecutionPayloadEnvelope>> getExecutionPayloads(
      Set<Bytes32> blockRoots);
}
