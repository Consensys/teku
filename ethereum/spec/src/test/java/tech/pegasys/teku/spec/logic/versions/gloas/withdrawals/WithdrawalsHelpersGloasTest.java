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

package tech.pegasys.teku.spec.logic.versions.gloas.withdrawals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class WithdrawalsHelpersGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 gloasSlot = UInt64.ONE;

  @Test
  void processWithdrawals_genesisDoesNotAdvanceWithdrawalIndices() {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.getGenesisSpec().getSchemaDefinitions());
    final ExecutionPayloadBid parentBid =
        dataStructureUtil.randomExecutionPayloadBid(
            UInt64.ZERO, UInt64.ZERO, Bytes32.ZERO, dataStructureUtil.randomBytes32());
    final MutableBeaconStateGloas state =
        MutableBeaconStateGloas.required(
            dataStructureUtil
                .stateBuilderGloas(16, 4, 4)
                .slot(gloasSlot)
                .latestBlockHash(Bytes32.ZERO)
                .latestExecutionPayloadBid(parentBid)
                .nextWithdrawalIndex(UInt64.valueOf(7))
                .builderPendingWithdrawals(
                    schemaDefinitions
                        .getBuilderPendingWithdrawalsSchema()
                        .createFromElements(
                            List.of(
                                schemaDefinitions
                                    .getBuilderPendingWithdrawalSchema()
                                    .create(
                                        dataStructureUtil.randomEth1Address(),
                                        UInt64.ONE,
                                        UInt64.ZERO))))
                .build()
                .createWritableCopy());

    final UInt64 preNextWithdrawalIndex = state.getNextWithdrawalIndex();
    final int prePendingWithdrawalsCount = state.getBuilderPendingWithdrawals().size();

    spec.atSlot(gloasSlot).getWithdrawalsHelpers().orElseThrow().processWithdrawals(state);

    assertThat(state.getNextWithdrawalIndex()).isEqualTo(preNextWithdrawalIndex);
    assertThat(state.getBuilderPendingWithdrawals()).hasSize(prePendingWithdrawalsCount);
  }
}
