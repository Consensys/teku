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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MiscHelpersElectraTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final Predicates predicates = new Predicates(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions());
  private final MiscHelpersElectra miscHelpersElectra =
      new MiscHelpersElectra(
          spec.getGenesisSpecConfig().toVersionElectra().orElseThrow(),
          predicates,
          schemaDefinitionsElectra);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final BeaconStateAccessorsElectra beaconStateAccessors =
      new BeaconStateAccessorsElectra(
          spec.getGenesisSpecConfig(),
          new PredicatesElectra(spec.getGenesisSpecConfig()),
          miscHelpersElectra);

  private final IntList validatorIndices = IntArrayList.of(1, 2, 3, 4, 5, 6, 7, 0);

  @Test
  public void isFormerDepositMechanismDisabled_returnsTrueIfDisabled() {
    final BeaconState preState = dataStructureUtil.randomBeaconState();

    final BeaconState state =
        BeaconStateElectra.required(preState)
            .updated(
                mutableState -> {
                  final UInt64 eth1DepositIndex = dataStructureUtil.randomUInt64();
                  mutableState.setEth1DepositIndex(eth1DepositIndex);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositReceiptsStartIndex(eth1DepositIndex);
                });

    assertThat(miscHelpersElectra.isFormerDepositMechanismDisabled(state)).isTrue();
  }

  @Test
  public void isFormerDepositMechanismDisabled_returnsFalseIfNotDisabled() {
    final BeaconState preState = dataStructureUtil.randomBeaconState();

    final BeaconState state =
        BeaconStateElectra.required(preState)
            .updated(
                mutableState -> {
                  mutableState.setEth1DepositIndex(UInt64.valueOf(64));
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositReceiptsStartIndex(
                          SpecConfigElectra.UNSET_DEPOSIT_RECEIPTS_START_INDEX);
                });

    assertThat(miscHelpersElectra.isFormerDepositMechanismDisabled(state)).isFalse();
  }

  @Test
  void consolidatedValidatorsMoreLikelyToPropose() {
    final int consolidationAmount = 16;
    final BeaconState state = randomStateWithConsolidatedValidator(consolidationAmount);
    int idx3ProposalCount = 0;
    for (int i = 1; i < 8; i++) {
      final UInt64 slot = UInt64.valueOf(8 + i);
      final Bytes32 seed =
          Hash.sha256(
              beaconStateAccessors.getSeed(state, UInt64.ONE, Domain.BEACON_PROPOSER),
              uint64ToBytes(slot));

      if (miscHelpersElectra.computeProposerIndex(state, validatorIndices, seed) == 3) {
        idx3ProposalCount++;
      }
    }
    assertThat(idx3ProposalCount).isEqualTo(4);
  }

  private BeaconState randomStateWithConsolidatedValidator(int consolidationAmount) {
    final BeaconState preState = dataStructureUtil.randomBeaconState(8);
    return BeaconStateElectra.required(preState)
        .updated(
            mutableState -> {
              mutableState
                  .getValidators()
                  .set(
                      3,
                      dataStructureUtil
                          .validatorBuilder()
                          .withdrawalCredentials(
                              dataStructureUtil.randomCompoundingWithdrawalCredentials())
                          .effectiveBalance(UInt64.THIRTY_TWO_ETH.times(consolidationAmount))
                          .build());
              mutableState
                  .getBalances()
                  .set(3, SszUInt64.of(UInt64.THIRTY_TWO_ETH.times(consolidationAmount)));
              mutableState.setSlot(UInt64.valueOf(8));
            });
  }
}
