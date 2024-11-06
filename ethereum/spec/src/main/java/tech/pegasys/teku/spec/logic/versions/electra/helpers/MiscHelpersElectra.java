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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class MiscHelpersElectra extends MiscHelpersDeneb {
  private final SpecConfigElectra specConfigElectra;
  private final PredicatesElectra predicatesElectra;

  public MiscHelpersElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final SchemaDefinitions schemaDefinitions) {
    super(
        SpecConfigDeneb.required(specConfig),
        predicates,
        SchemaDefinitionsDeneb.required(schemaDefinitions));
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
    this.predicatesElectra = PredicatesElectra.required(predicates);
  }

  public static MiscHelpersElectra required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  @Override
  public int computeProposerIndex(
      final BeaconState state, final IntList indices, final Bytes32 seed) {
    return computeProposerIndex(
        state,
        indices,
        seed,
        SpecConfigElectra.required(specConfig).getMaxEffectiveBalanceElectra());
  }

  @Override
  public UInt64 getMaxEffectiveBalance(final Validator validator) {
    return predicatesElectra.hasCompoundingWithdrawalCredential(validator)
        ? specConfigElectra.getMaxEffectiveBalanceElectra()
        : specConfigElectra.getMinActivationBalance();
  }

  @Override
  public Optional<MiscHelpersElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    // if the next deposit to be processed by Eth1Data poll has the index of the first deposit
    // processed with the new deposit flow, i.e. `eth1_deposit_index ==
    // deposit_requests_start_index`, we should stop Eth1Data deposits processing
    return state
        .getEth1DepositIndex()
        .equals(BeaconStateElectra.required(state).getDepositRequestsStartIndex());
  }

  @Override
  public int getMaxRequestBlobSidecars() {
    return SpecConfigElectra.required(specConfig).getMaxRequestBlobSidecarsElectra();
  }

  @Override
  public Optional<Integer> getMaxBlobsPerBlock() {
    return Optional.of(SpecConfigElectra.required(specConfig).getMaxBlobsPerBlockElectra());
  }

  public Optional<Integer> getTargetBlobsPerBlock() {
    return Optional.of(SpecConfigElectra.required(specConfig).getTargetBlobsPerBlockElectra());
  }

  @Override
  public Optional<Integer> getBlobSidecarSubnetCount() {
    return Optional.of(SpecConfigElectra.required(specConfig).getBlobSidecarSubnetCountElectra());
  }
}
