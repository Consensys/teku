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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class MiscHelpersElectra extends MiscHelpersDeneb {

  public static MiscHelpersElectra required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  private final SpecConfigElectra specConfigElectra;

  public MiscHelpersElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final SchemaDefinitionsElectra schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
    this.specConfigElectra = specConfig;
  }

  @Override
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    // if the next deposit to be processed by Eth1Data poll has the index of the first deposit
    // processed with the new deposit flow, i.e. `eth1_deposit_index ==
    // deposit_receipts_start_index`, we should stop Eth1Data deposits processing
    return state
        .getEth1DepositIndex()
        .equals(BeaconStateElectra.required(state).getDepositReceiptsStartIndex());
  }

  public UInt64 computeSubnetForDataColumnSidecar(UInt64 columnIndex) {
    return columnIndex.mod(specConfigElectra.getDataColumnSidecarSubnetCount());
  }

  public Set<UInt64> computeCustodyColumnIndexes(
      final UInt256 nodeId, final UInt64 epoch, final int subnetCount) {
    // TODO: implement whatever formula is finalized
    Set<UInt64> subnets =
        new HashSet<>(computeDataColumnSidecarBackboneSubnets(nodeId, epoch, subnetCount));
    return Stream.iterate(UInt64.ZERO, UInt64::increment)
        .limit(specConfigElectra.getNumberOfColumns().intValue())
        .filter(columnIndex -> subnets.contains(computeSubnetForDataColumnSidecar(columnIndex)))
        .collect(Collectors.toSet());
  }

  public List<UInt64> computeDataColumnSidecarBackboneSubnets(
      final UInt256 nodeId, final UInt64 epoch, final int subnetCount) {
    // TODO: implement whatever formula is finalized
    return IntStream.range(0, subnetCount)
        .mapToObj(index -> computeSubscribedSubnet(nodeId, epoch, index))
        .toList();
  }

  @Override
  public Optional<MiscHelpersElectra> toVersionElectra() {
    return Optional.of(this);
  }
}
