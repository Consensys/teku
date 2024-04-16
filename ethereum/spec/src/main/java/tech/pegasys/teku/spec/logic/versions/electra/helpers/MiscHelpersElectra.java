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

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class MiscHelpersElectra extends MiscHelpersDeneb {

  public MiscHelpersElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final SchemaDefinitionsElectra schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
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

  public List<UInt64> computeDataColumnSidecarBackboneSubnets(final UInt256 nodeId, final UInt64 epoch, final int subnetCount) {
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
