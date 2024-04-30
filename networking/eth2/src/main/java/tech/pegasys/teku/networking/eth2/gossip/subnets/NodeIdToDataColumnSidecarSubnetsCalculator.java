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
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;

@FunctionalInterface
public interface NodeIdToDataColumnSidecarSubnetsCalculator {

  Optional<SszBitvector> calculateSubnets(NodeId nodeId, int extraSubnetCount);

  NodeIdToDataColumnSidecarSubnetsCalculator NOOP = (nodeId, extraSubnetCount) -> Optional.empty();

  /** Creates a calculator instance for the specific slot */
  private static NodeIdToDataColumnSidecarSubnetsCalculator createAtSlot(
      SpecConfigEip7594 config, MiscHelpersEip7594 miscHelpers, UInt64 currentSlot) {
    UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(currentSlot);
    SszBitvectorSchema<SszBitvector> bitvectorSchema =
        SszBitvectorSchema.create(config.getDataColumnSidecarSubnetCount());
    return (nodeId, extraSubnetCount) -> {
      List<UInt64> nodeSubnets =
          miscHelpers.computeDataColumnSidecarBackboneSubnets(
              UInt256.fromBytes(nodeId.toBytes()),
              currentEpoch,
              config.getCustodyRequirement() + extraSubnetCount);
      return Optional.of(
          bitvectorSchema.ofBits(nodeSubnets.stream().map(UInt64::intValue).toList()));
    };
  }

  /** Create an instance base on the current slot */
  static NodeIdToDataColumnSidecarSubnetsCalculator create(
      Spec spec, Supplier<Optional<UInt64>> currentSlotSupplier) {

    return (nodeId, extraSubnetCount) ->
        currentSlotSupplier
            .get()
            .flatMap(
                slot -> {
                  SpecVersion specVersion = spec.atSlot(slot);
                  final NodeIdToDataColumnSidecarSubnetsCalculator calculatorAtSlot;
                  if (specVersion.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.EIP7594)) {
                    calculatorAtSlot =
                        createAtSlot(
                            SpecConfigEip7594.required(specVersion.getConfig()),
                            MiscHelpersEip7594.required(specVersion.miscHelpers()),
                            slot);
                  } else {
                    calculatorAtSlot = NOOP;
                  }
                  return calculatorAtSlot.calculateSubnets(nodeId, extraSubnetCount);
                });
  }
}
