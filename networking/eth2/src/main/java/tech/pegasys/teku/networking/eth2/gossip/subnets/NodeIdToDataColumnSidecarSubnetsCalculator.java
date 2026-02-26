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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import com.google.common.base.Supplier;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

@FunctionalInterface
public interface NodeIdToDataColumnSidecarSubnetsCalculator {

  Optional<SszBitvector> calculateSubnets(PeerId peerId, Optional<Integer> groupCount);

  NodeIdToDataColumnSidecarSubnetsCalculator NOOP = (peerId, subnetCount) -> Optional.empty();

  // DataColumnSidecarSubnet calculation currently only works for CandidatePeers, not for existing
  // peers.
  static NodeIdToDataColumnSidecarSubnetsCalculator create(
      final Spec spec, final Supplier<Optional<UInt64>> currentSlotSupplier) {

    return (peerId, groupCount) ->
        currentSlotSupplier
            .get()
            .flatMap(
                slot -> {
                  final SpecVersion version = spec.atSlot(slot);
                  if (version.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
                    final SpecConfigFulu config = SpecConfigFulu.required(version.getConfig());
                    final List<UInt64> subnets =
                        MiscHelpersFulu.required(version.miscHelpers())
                            .computeDataColumnSidecarBackboneSubnets(
                                peerId.toUInt256().orElseThrow(), // Only for candidate peers
                                groupCount.orElse(config.getCustodyRequirement()));
                    return Optional.of(
                        SszBitvectorSchema.create(config.getDataColumnSidecarSubnetCount())
                            .ofBits(subnets.stream().map(UInt64::intValue).toList()));
                  }
                  return Optional.empty();
                });
  }
}
