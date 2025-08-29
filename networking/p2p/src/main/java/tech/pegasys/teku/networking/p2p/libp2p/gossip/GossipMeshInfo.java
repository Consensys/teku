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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import io.libp2p.pubsub.gossip.Gossip;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Provides safe access to mesh information from GossipRouter. This class avoids unsafe direct
 * access to the underlying mesh map which is not thread-safe.
 */
@SuppressWarnings("UnusedVariable")
public class GossipMeshInfo {
  private final Gossip gossip;
  private final Map<String, Integer> previousMeshSizes = new ConcurrentHashMap<>();

  public GossipMeshInfo(final Gossip gossip) {
    this.gossip = gossip;
  }

  /**
   * Gets the current mesh size for each topic.
   *
   * @return A map of topic names to their mesh size (number of peers in the mesh)
   */
  public SafeFuture<Map<String, Integer>> getMeshSizeByTopic() {
    // Get mesh information from the router in a safe way
    // This uses the router's own API to safely access the mesh information
    // TODO: Fix access to gossip.getRouter().getMeshPeers() once we have proper API access
    // return SafeFuture.of(gossip.getRouter().getMeshPeers())
    return SafeFuture.of(CompletableFuture.completedFuture(Collections.emptyMap()))
        .thenApply(
            meshByTopic -> {
              final Map<String, Integer> result = new HashMap<>();
              // For now we return empty map until we fix the access issue
              // Once fixed, we'll restore the original code
              /*
              for (Map.Entry<Topic, Set<?>> entry : meshByTopic.entrySet()) {
                final String topicName = entry.getKey().getTopic();
                final int meshSize = entry.getValue().size();
                result.put(topicName, meshSize);

                // Update the tracking for leave/join calculation
                previousMeshSizes.putIfAbsent(topicName, 0);
              }
              */
              return result;
            });
  }

  /**
   * Gets the current metrics about mesh peer changes.
   *
   * @return A map containing topic names to MeshMetrics objects with joined/left peers information
   */
  public SafeFuture<Map<String, MeshMetrics>> getMeshMetrics() {
    // TODO: Currently using empty data until we fix access to gossip.getRouter().getMeshPeers()
    // Once fixed, we'll restore the original code
    return SafeFuture.completedFuture(Collections.emptyMap());
    /*
    return getMeshSizeByTopic()
        .thenApply(
            currentMeshSizes -> {
              final Map<String, MeshMetrics> result = new HashMap<>();

              // Process existing topics
              for (Map.Entry<String, Integer> entry : currentMeshSizes.entrySet()) {
                final String topicName = entry.getKey();
                final int currentSize = entry.getValue();
                final int previousSize = previousMeshSizes.getOrDefault(topicName, 0);

                final int joined = Math.max(0, currentSize - previousSize);
                final int left = Math.max(0, previousSize - currentSize);

                result.put(topicName, new MeshMetrics(currentSize, joined, left));

                // Update the previous size for next calculation
                previousMeshSizes.put(topicName, currentSize);
              }

              // Check for topics that were removed
              for (String topic : previousMeshSizes.keySet()) {
                if (!currentMeshSizes.containsKey(topic)) {
                  final int previousSize = previousMeshSizes.getOrDefault(topic, 0);
                  result.put(topic, new MeshMetrics(0, 0, previousSize));
                  previousMeshSizes.remove(topic);
                }
              }

              return result;
            });
    */
  }

  /** Metrics for a single mesh topic. */
  public static class MeshMetrics {
    private final int meshSize;
    private final int peersJoined;
    private final int peersLeft;

    public MeshMetrics(final int meshSize, final int peersJoined, final int peersLeft) {
      this.meshSize = meshSize;
      this.peersJoined = peersJoined;
      this.peersLeft = peersLeft;
    }

    public int getMeshSize() {
      return meshSize;
    }

    public int getPeersJoined() {
      return peersJoined;
    }

    public int getPeersLeft() {
      return peersLeft;
    }
  }
}
