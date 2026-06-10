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

package tech.pegasys.teku.reference.phase0.gossip;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.storage.client.RecentChainData;

public final class GossipValidationHelperFactory {

  private GossipValidationHelperFactory() {}

  public static GossipValidationHelper createAttestationGossipValidationHelper(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final Optional<Checkpoint> finalizedCheckpointOverride) {
    return finalizedCheckpointOverride
        .<GossipValidationHelper>map(
            finalizedCheckpoint ->
                new GossipValidationHelper(spec, recentChainData, metricsSystem) {
                  @Override
                  public boolean currentFinalizedCheckpointIsAncestorOfAttestationBlock(
                      final Bytes32 blockRoot) {
                    return spec.getAncestor(
                            getForkChoiceStrategy(),
                            blockRoot,
                            finalizedCheckpoint.getEpochStartSlot(spec))
                        .map(ancestorRoot -> ancestorRoot.equals(finalizedCheckpoint.getRoot()))
                        .orElse(false);
                  }
                })
        .orElseGet(() -> new GossipValidationHelper(spec, recentChainData, metricsSystem));
  }

  public static GossipValidationHelper createBlockGossipValidationHelper(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final Optional<Checkpoint> finalizedCheckpointOverride) {
    return finalizedCheckpointOverride
        .<GossipValidationHelper>map(
            finalizedCheckpoint ->
                new GossipValidationHelper(spec, recentChainData, metricsSystem) {
                  @Override
                  public boolean currentFinalizedCheckpointIsAncestorOfBlock(
                      final UInt64 blockSlot, final Bytes32 blockParentRoot) {
                    if (blockSlot.isLessThanOrEqualTo(
                        finalizedCheckpoint.getEpochStartSlot(spec))) {
                      return false;
                    }
                    return spec.getAncestor(
                            getForkChoiceStrategy(),
                            blockParentRoot,
                            finalizedCheckpoint.getEpochStartSlot(spec))
                        .map(ancestorRoot -> ancestorRoot.equals(finalizedCheckpoint.getRoot()))
                        .orElse(false);
                  }
                })
        .orElseGet(() -> new GossipValidationHelper(spec, recentChainData, metricsSystem));
  }
}
