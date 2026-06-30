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

package tech.pegasys.teku.spec.logic.versions.gloas.forktransition;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.ValidatorIndexCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class GloasStateUpgradeTest {

  private static final UInt64 GLOAS_EPOCH = UInt64.valueOf(2);

  private final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldCarryValidatorIndexCacheAcrossUpgrade() {
    final BeaconStateFulu preState =
        BeaconStateFulu.required(
            dataStructureUtil
                .stateBuilder(SpecMilestone.FULU, 64, 0)
                .setSlotToStartOfEpoch(GLOAS_EPOCH)
                .build()
                .updated(this::activateAllValidators));
    final ValidatorIndexCache preStateValidatorIndexCache =
        BeaconStateCache.getTransitionCaches(preState).getValidatorIndexCache();
    preStateValidatorIndexCache.getValidatorIndex(
        preState, preState.getValidators().get(0).getPublicKey());

    final GloasStateUpgrade stateUpgrade = createStateUpgrade();
    final BeaconStateGloas postState = BeaconStateGloas.required(stateUpgrade.upgrade(preState));

    assertThat(BeaconStateCache.getTransitionCaches(postState).getValidatorIndexCache())
        .isSameAs(preStateValidatorIndexCache);
  }

  private GloasStateUpgrade createStateUpgrade() {
    final SpecVersion gloasSpecVersion = spec.atEpoch(GLOAS_EPOCH);
    return new GloasStateUpgrade(
        SpecConfigGloas.required(gloasSpecVersion.getConfig()),
        SchemaDefinitionsGloas.required(gloasSpecVersion.getSchemaDefinitions()),
        BeaconStateAccessorsGloas.required(gloasSpecVersion.beaconStateAccessors()),
        PredicatesGloas.required(gloasSpecVersion.predicates()),
        BeaconStateMutatorsGloas.required(gloasSpecVersion.beaconStateMutators()),
        MiscHelpersGloas.required(gloasSpecVersion.miscHelpers()),
        gloasSpecVersion.getValidatorsUtil());
  }

  private void activateAllValidators(final MutableBeaconState state) {
    final SszMutableList<Validator> validators = state.getValidators();
    for (int i = 0; i < validators.size(); i++) {
      validators.update(
          i,
          validator ->
              validator
                  .withActivationEligibilityEpoch(UInt64.ZERO)
                  .withActivationEpoch(UInt64.ZERO)
                  .withExitEpoch(FAR_FUTURE_EPOCH)
                  .withWithdrawableEpoch(FAR_FUTURE_EPOCH));
    }
  }
}
