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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconStateMutatorsElectraTest {

  private final Spec spec = TestSpecFactory.createMainnetElectra();
  private final PredicatesElectra predicates = new PredicatesElectra(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions());
  private final SpecConfigElectra specConfig =
      spec.getGenesisSpecConfig().toVersionElectra().orElseThrow();
  private final MiscHelpersElectra miscHelpersElectra =
      new MiscHelpersElectra(specConfig, predicates, schemaDefinitionsElectra);
  final BeaconStateAccessorsElectra stateAccessorsElectra =
      new BeaconStateAccessorsElectra(specConfig, predicates, miscHelpersElectra);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private BeaconStateMutatorsElectra stateMutatorsElectra;

  @BeforeEach
  public void setUp() {
    stateMutatorsElectra =
        new BeaconStateMutatorsElectra(
            specConfig, miscHelpersElectra, stateAccessorsElectra, schemaDefinitionsElectra);
  }

  @Test
  public void queueExcessActiveBalance_withExcessBalance_ShouldCreatePendingBalanceDeposit() {
    final UInt64 minActivationBalance = specConfig.getMinActivationBalance();
    final long excessBalance = 1L;
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeValidator(minActivationBalance.plus(excessBalance))
                .build());

    final BeaconStateElectra postState =
        preState.updatedElectra(state -> stateMutatorsElectra.queueExcessActiveBalance(state, 0));
    final SszList<PendingBalanceDeposit> postPendingBalanceDeposits =
        postState.getPendingBalanceDeposits();

    assertThat(postPendingBalanceDeposits.size()).isEqualTo(1);
    assertThat(postPendingBalanceDeposits.get(0).getAmount())
        .isEqualTo(UInt64.valueOf(excessBalance));
  }

  @Test
  public void queueExcessActiveBalance_withoutExcessBalance_ShouldNotCreatePendingBalanceDeposit() {
    final UInt64 minActivationBalance = specConfig.getMinActivationBalance();
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeValidator(minActivationBalance)
                .build());

    final BeaconStateElectra postState =
        preState.updatedElectra(state -> stateMutatorsElectra.queueExcessActiveBalance(state, 0));
    final SszList<PendingBalanceDeposit> postPendingBalanceDeposits =
        postState.getPendingBalanceDeposits();

    assertThat(postPendingBalanceDeposits.size()).isEqualTo(0);
  }

  @Test
  public void queueExcessActiveBalance_correctlyAppendsNewBalanceDeposits() {
    final UInt64 minActivationBalance = specConfig.getMinActivationBalance();
    final long excessBalance = 1L;
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeValidator(minActivationBalance.plus(excessBalance))
                .activeValidator(minActivationBalance.plus(excessBalance))
                .build());

    BeaconStateElectra postState =
        preState.updatedElectra(state -> stateMutatorsElectra.queueExcessActiveBalance(state, 0));

    assertThat(postState.getPendingBalanceDeposits().size()).isEqualTo(1);

    postState =
        postState.updatedElectra(state -> stateMutatorsElectra.queueExcessActiveBalance(state, 1));
    assertThat(postState.getPendingBalanceDeposits().size()).isEqualTo(2);
  }
}
