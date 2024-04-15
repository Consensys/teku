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

package tech.pegasys.teku.spec.logic.versions.electra.forktransition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ElectraStateUpgradeTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final PredicatesElectra predicatesElectra =
      new PredicatesElectra(spec.getGenesisSpecConfig());
  private final MiscHelpersElectra miscHelpersElectra =
      new MiscHelpersElectra(
          spec.getGenesisSpecConfig(), predicatesElectra, spec.getGenesisSchemaDefinitions());
  final BeaconStateAccessorsElectra stateAccessorsElectra =
      new BeaconStateAccessorsElectra(
          spec.getGenesisSpecConfig(), predicatesElectra, miscHelpersElectra);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void canUpgradeFromDeneb() {
    final BeaconStateDeneb pre =
        BeaconStateDeneb.required(
            dataStructureUtil
                .stateBuilder(SpecMilestone.DENEB, 1, 0)
                .slot(UInt64.valueOf(80_000L))
                .build());
    final ElectraStateUpgrade upgrade =
        new ElectraStateUpgrade(
            SpecConfigElectra.required(spec.getGenesisSpecConfig()),
            SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions()),
            stateAccessorsElectra);

    final BeaconStateElectra post = upgrade.upgrade(pre);

    assertThat(post.getDepositBalanceToConsume()).isEqualTo(UInt64.ZERO);
    // min churn - churn/balance_increment = (64 *10^9) - 64
    assertThat(post.getExitBalanceToConsume()).isEqualTo(UInt64.valueOf(63_999_999_936L));
    assertThat(post.getEarliestExitEpoch()).isEqualTo(UInt64.ONE);
    assertThat(post.getConsolidationBalanceToConsume()).isEqualTo(UInt64.ZERO);
    // 80_000/8 (slots -> epochs) + max_seed_lookahead + 1
    assertThat(post.getEarliestConsolidationEpoch()).isEqualTo(UInt64.valueOf(10005));
    assertThat(post.getPendingBalanceDeposits()).isEmpty();
    assertThat(post.getPendingConsolidations()).isEmpty();
    assertThat(post.getPendingPartialWithdrawals()).isEmpty();
  }
}
