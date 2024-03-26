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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
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
}
