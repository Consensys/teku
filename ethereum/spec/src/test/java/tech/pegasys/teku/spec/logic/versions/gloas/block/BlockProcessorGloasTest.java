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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockProcessorGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 gloasSlot = UInt64.ONE;

  @Test
  void processParentExecutionPayload_genesisDoesNotUpdateLatestBlockHash()
      throws BlockProcessingException {
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
                .build()
                .createWritableCopy());

    final ExecutionPayloadBid childBid =
        dataStructureUtil.randomExecutionPayloadBid(
            Bytes32.ZERO, gloasSlot, UInt64.ONE, UInt64.ZERO, UInt64.ZERO);
    final BeaconBlock block =
        dataStructureUtil.randomBeaconBlock(
            gloasSlot,
            dataStructureUtil.randomBeaconBlockBody(
                gloasSlot,
                builder -> {
                  builder.signedExecutionPayloadBid(
                      dataStructureUtil.randomSignedExecutionPayloadBid(childBid));
                  builder.parentExecutionRequests(dataStructureUtil.emptyExecutionRequests());
                }));

    spec.getBlockProcessor(gloasSlot)
        .processParentExecutionPayload(
            state,
            block,
            spec.atSlot(gloasSlot).beaconStateMutators().createValidatorExitContextSupplier(state));

    assertThat(state.getLatestBlockHash()).isEqualTo(Bytes32.ZERO);
  }
}
