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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MiscHelpersGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil data = new DataStructureUtil(spec);

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
  public void isExecutionPayloadBidForEmptyParent_shouldBeFalseWhenBidAlsoMatchesFullParent() {
    final Bytes32 fullParentBlockHash = data.randomBytes32();
    final ExecutionPayloadBid latestExecutionPayloadBid =
        data.randomExecutionPayloadBid(
            data.randomSlot(),
            data.randomBuilderIndex(),
            fullParentBlockHash,
            data.randomBytes32());
    final BeaconStateGloas state =
        BeaconStateGloas.required(
            data.randomBeaconState()
                .updated(
                    mutableState -> {
                      final MutableBeaconStateGloas stateGloas =
                          MutableBeaconStateGloas.required(mutableState);
                      stateGloas.setLatestBlockHash(fullParentBlockHash);
                      stateGloas.setLatestExecutionPayloadBid(latestExecutionPayloadBid);
                    }));
    final ExecutionPayloadBid childBid =
        data.randomExecutionPayloadBid(
            fullParentBlockHash,
            data.randomSlot(),
            data.randomBuilderIndex(),
            UInt64.ZERO,
            UInt64.ZERO);

    assertThat(miscHelpers.isExecutionPayloadBidForFullParent(state, childBid)).isTrue();
    assertThat(miscHelpers.isExecutionPayloadBidForEmptyParent(state, childBid)).isFalse();
  }
}
