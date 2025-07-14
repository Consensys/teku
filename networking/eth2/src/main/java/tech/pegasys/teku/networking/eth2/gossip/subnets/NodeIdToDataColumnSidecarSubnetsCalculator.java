/*
 * Copyright Consensys Software Inc., 2024
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
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

@FunctionalInterface
public interface NodeIdToDataColumnSidecarSubnetsCalculator {

  Optional<SszBitvector> calculateSubnets(UInt256 nodeId, Optional<Integer> groupCount);

  NodeIdToDataColumnSidecarSubnetsCalculator NOOP = (nodeId, subnetCount) -> Optional.empty();

  /** Creates a calculator instance for the specific slot */
  @SuppressWarnings("UnusedVariable")
  private static NodeIdToDataColumnSidecarSubnetsCalculator createAtSlot(
      final SpecConfigFulu config, final MiscHelpers miscHelpers, final UInt64 currentSlot) {
    SszBitvectorSchema<SszBitvector> bitvectorSchema =
        SszBitvectorSchema.create(config.getDataColumnSidecarSubnetCount());
    return (nodeId, groupCount) -> {
      List<UInt64> nodeSubnets =
          MiscHelpersFulu.required(miscHelpers)
              .computeDataColumnSidecarBackboneSubnets(
                  nodeId, groupCount.orElse(config.getCustodyRequirement()));
      return Optional.of(
          bitvectorSchema.ofBits(nodeSubnets.stream().map(UInt64::intValue).toList()));
    };
  }

  /** Create an instance base on the current slot */
  static NodeIdToDataColumnSidecarSubnetsCalculator create(
      final Spec spec, final Supplier<Optional<UInt64>> currentSlotSupplier) {

    return (nodeId, groupCount) ->
        currentSlotSupplier
            .get()
            .flatMap(
                slot -> {
                  final SpecVersion specVersion = spec.atSlot(slot);
                  final NodeIdToDataColumnSidecarSubnetsCalculator calculatorAtSlot;
                  if (specVersion.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
                    calculatorAtSlot =
                        createAtSlot(
                            SpecConfigFulu.required(specVersion.getConfig()),
                            specVersion.miscHelpers(),
                            slot);
                  } else {
                    calculatorAtSlot = NOOP;
                  }
                  return calculatorAtSlot.calculateSubnets(nodeId, groupCount);
                });
  }
}
