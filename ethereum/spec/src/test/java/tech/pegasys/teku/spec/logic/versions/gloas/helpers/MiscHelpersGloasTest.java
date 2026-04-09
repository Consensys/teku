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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MiscHelpersGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final MiscHelpersGloas miscHelpers =
      MiscHelpersGloas.required(spec.getGenesisSpec().miscHelpers());

  private final PredicatesGloas predicates =
      PredicatesGloas.required(spec.getGenesisSpec().predicates());

  @Test
  public void roundTrip_convertBuilderIndexToValidatorIndex() {
    final UInt64 builderIndex = UInt64.valueOf(42);
    final UInt64 validatorIndex = miscHelpers.convertBuilderIndexToValidatorIndex(builderIndex);
    assertThat(predicates.isBuilderIndex(validatorIndex)).isTrue();
    assertThat(miscHelpers.convertValidatorIndexToBuilderIndex(validatorIndex))
        .isEqualTo(builderIndex);
  }

  @Test
  public void computeBalanceWeightedSelection_shouldExcludeSlashedValidators() {
    final BeaconState baseState = dataStructureUtil.randomBeaconState();
    final int slashedIndex = 0;
    // Slash validator at index 0
    final BeaconState state =
        baseState.updated(
            mutableState -> {
              final Validator validator = mutableState.getValidators().get(slashedIndex);
              mutableState.getValidators().set(slashedIndex, validator.withSlashed(true));
            });

    final IntList indices = new IntArrayList();
    for (int i = 0; i < Math.min(10, state.getValidators().size()); i++) {
      indices.add(i);
    }
    final Bytes32 seed = dataStructureUtil.randomBytes32();

    final IntList selected =
        miscHelpers.computeBalanceWeightedSelection(state, indices, seed, 5, true);

    assertThat(selected.size()).isEqualTo(5);
    assertThat(selected.contains(slashedIndex)).isFalse();
  }

  @Test
  public void computeBalanceWeightedSelection_shouldExcludeMultipleSlashedValidators() {
    final BeaconState baseState = dataStructureUtil.randomBeaconState();
    final int slashedIndex0 = 0;
    final int slashedIndex1 = 1;
    final int slashedIndex2 = 2;
    // Slash validators at indices 0, 1, 2
    final BeaconState state =
        baseState.updated(
            mutableState -> {
              for (int idx : new int[] {slashedIndex0, slashedIndex1, slashedIndex2}) {
                final Validator validator = mutableState.getValidators().get(idx);
                mutableState.getValidators().set(idx, validator.withSlashed(true));
              }
            });

    final IntList indices = new IntArrayList();
    for (int i = 0; i < Math.min(10, state.getValidators().size()); i++) {
      indices.add(i);
    }
    final Bytes32 seed = dataStructureUtil.randomBytes32();

    final IntList selected =
        miscHelpers.computeBalanceWeightedSelection(state, indices, seed, 5, true);

    assertThat(selected.size()).isEqualTo(5);
    assertThat(selected.contains(slashedIndex0)).isFalse();
    assertThat(selected.contains(slashedIndex1)).isFalse();
    assertThat(selected.contains(slashedIndex2)).isFalse();
  }
}
