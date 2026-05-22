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

package tech.pegasys.teku.spec.logic.versions.gloas.block;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = GLOAS)
class BlockProcessorGloasTest {

  @TestTemplate
  void calculatePayloadExpectedWithdrawalsUsesEffectiveState(final SpecContext specContext)
      throws Exception {
    final Spec spec = specContext.getSpec();
    final DataStructureUtil dataStructureUtil = specContext.getDataStructureUtil();
    final UInt64 slot = UInt64.valueOf(16);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionRequests parentExecutionRequests =
        schemaDefinitions.getExecutionRequestsSchema().getDefault();
    final Bytes32 parentBlockHash = Bytes32.fromHexString("0x01");
    final UInt64 builderIndex = UInt64.ZERO;
    final UInt64 parentPayloadValue = UInt64.valueOf(1234);
    final ExecutionPayloadBid latestExecutionPayloadBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .create(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                parentBlockHash,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomEth1Address(),
                dataStructureUtil.randomUInt64(),
                builderIndex,
                UInt64.ZERO,
                parentPayloadValue,
                UInt64.ZERO,
                dataStructureUtil.randomBlobKzgCommitments(),
                parentExecutionRequests.hashTreeRoot());
    Bytes32 latestBlockHash = dataStructureUtil.randomBytes32();
    while (latestBlockHash.equals(latestExecutionPayloadBid.getBlockHash())) {
      latestBlockHash = dataStructureUtil.randomBytes32();
    }
    final Bytes32 latestBlockHashForState = latestBlockHash;

    final BeaconState originalState =
        dataStructureUtil
            .randomBeaconState(slot)
            .updated(
                state -> {
                  final MutableBeaconStateGloas stateGloas =
                      MutableBeaconStateGloas.required(state);
                  stateGloas.setLatestBlockHash(latestBlockHashForState);
                  stateGloas.setLatestExecutionPayloadBid(latestExecutionPayloadBid);
                  stateGloas.setNextWithdrawalBuilderIndex(builderIndex);
                  stateGloas.setBuilders(
                      BeaconStateSchemaGloas.required(schemaDefinitions.getBeaconStateSchema())
                          .getBuildersSchema()
                          .createFromElements(
                              List.of(
                                  dataStructureUtil
                                      .builderBuilder()
                                      .balance(parentPayloadValue)
                                      .build())));
                });

    assertThat(BeaconStateGloas.required(originalState).getLatestBlockHash())
        .isNotEqualTo(
            BeaconStateGloas.required(originalState).getLatestExecutionPayloadBid().getBlockHash());

    final Bytes32 originalStateRoot = originalState.hashTreeRoot();
    final BlockProcessorGloas blockProcessor = (BlockProcessorGloas) spec.getBlockProcessor(slot);
    final BeaconState expectedEffectiveState =
        originalState.updated(
            state -> {
              final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
              blockProcessor.applyParentExecutionPayload(
                  stateGloas,
                  parentExecutionRequests,
                  () -> {
                    throw new AssertionError("No validator exits expected");
                  });
              blockProcessor.processWithdrawals(stateGloas, Optional.empty());
            });

    final SszList<Withdrawal> actual =
        blockProcessor.calculatePayloadExpectedWithdrawals(originalState, parentExecutionRequests);

    assertThat(BeaconStateGloas.required(expectedEffectiveState).getPayloadExpectedWithdrawals())
        .isNotEmpty();
    assertThat(BeaconStateGloas.required(expectedEffectiveState).getPayloadExpectedWithdrawals())
        .isNotEqualTo(spec.getExpectedWithdrawals(originalState).orElseThrow());
    assertThat(actual)
        .isEqualTo(
            BeaconStateGloas.required(expectedEffectiveState).getPayloadExpectedWithdrawals());
    assertThat(originalState.hashTreeRoot()).isEqualTo(originalStateRoot);
    assertThat(BeaconStateGloas.required(originalState).getLatestBlockHash())
        .isNotEqualTo(
            BeaconStateGloas.required(originalState).getLatestExecutionPayloadBid().getBlockHash());
  }
}
