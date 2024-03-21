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

package tech.pegasys.teku.spec.logic.versions.electra.block;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.util.DepositReceiptsUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDenebTest;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

class BlockProcessorElectraTest extends BlockProcessorDenebTest {

  protected DepositReceiptsUtil depositReceiptsUtil = new DepositReceiptsUtil(spec);

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetElectra();
  }

  @Test
  public void verifiesOutstandingEth1DepositsAreProcessed() {
    final BeaconState state =
        createBeaconState()
            .updated(
                mutableState -> {
                  final UInt64 eth1DepositCount = UInt64.valueOf(25);
                  mutableState.setEth1Data(
                      new Eth1Data(
                          dataStructureUtil.randomBytes32(),
                          eth1DepositCount,
                          dataStructureUtil.randomBytes32()));
                  final UInt64 eth1DepositIndex = UInt64.valueOf(13);
                  mutableState.setEth1DepositIndex(eth1DepositIndex);
                  final UInt64 depositReceiptsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositReceiptsStartIndex(depositReceiptsStartIndex);
                });

    final BeaconBlockBody body =
        dataStructureUtil.randomFullBeaconBlockBody(
            // 20 - 13 = 7
            builder -> builder.deposits(dataStructureUtil.randomSszDeposits(7)));

    getBlockProcessor(state).verifyOutstandingDepositsAreProcessed(state, body);
  }

  @Test
  public void
      verifiesNoOutstandingEth1DepositsAreProcessedWhenFormerDepositMechanismHasBeenDisabled() {
    final BeaconState state =
        createBeaconState()
            .updated(
                mutableState -> {
                  final UInt64 eth1DepositCount = UInt64.valueOf(25);
                  mutableState.setEth1Data(
                      new Eth1Data(
                          dataStructureUtil.randomBytes32(),
                          eth1DepositCount,
                          dataStructureUtil.randomBytes32()));
                  final UInt64 eth1DepositIndex = UInt64.valueOf(20);
                  mutableState.setEth1DepositIndex(eth1DepositIndex);
                  final UInt64 depositReceiptsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositReceiptsStartIndex(depositReceiptsStartIndex);
                });

    final BeaconBlockBody body =
        dataStructureUtil.randomFullBeaconBlockBody(
            // 20 - 20 = 0
            modifier -> modifier.deposits(dataStructureUtil.randomSszDeposits(0)));

    getBlockProcessor(state).verifyOutstandingDepositsAreProcessed(state, body);
  }

  @Test
  public void processesDepositReceipts() {
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState ->
                        MutableBeaconStateElectra.required(mutableState)
                            .setDepositReceiptsStartIndex(
                                SpecConfigElectra.UNSET_DEPOSIT_RECEIPTS_START_INDEX)));
    final int numberOfValidators = preState.getValidators().size();

    final SszListSchema<DepositReceipt, ? extends SszList<DepositReceipt>> depositReceiptsSchema =
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
            .getExecutionPayloadSchema()
            .getDepositReceiptsSchemaRequired();
    final int depositReceiptsCount = 3;
    final List<DepositReceipt> depositReceipts =
        IntStream.range(0, depositReceiptsCount)
            .mapToObj(
                i ->
                    depositReceiptsUtil.createDepositReceipt(
                        preState.getSlot(), UInt64.valueOf(numberOfValidators + i)))
            .toList();

    final BeaconStateElectra state =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processDepositReceipts(
                            MutableBeaconStateElectra.required(mutableState),
                            depositReceiptsSchema.createFromElements(depositReceipts))));

    // verify deposit_receipts_start_index has been set
    assertThat(state.getDepositReceiptsStartIndex()).isEqualTo(UInt64.valueOf(numberOfValidators));
    // verify validators have been added to the state
    assertThat(state.getValidators().size()).isEqualTo(numberOfValidators + depositReceiptsCount);
  }

  private BlockProcessorElectra getBlockProcessor(final BeaconState state) {
    return (BlockProcessorElectra) spec.getBlockProcessor(state.getSlot());
  }
}
