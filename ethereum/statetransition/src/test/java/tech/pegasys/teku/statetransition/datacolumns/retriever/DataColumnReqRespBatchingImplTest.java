/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnReqRespBatchingImplTest {
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final BatchDataColumnsByRangeReqResp batchRpc = mock(BatchDataColumnsByRangeReqResp.class);
  final DataColumnReqRespBatchingImpl dataColumnReqResp =
      new DataColumnReqRespBatchingImpl(batchRpc);

  @Test
  @SuppressWarnings("JavaCase")
  public void sanityCheck() {
    final SignedBeaconBlockHeader blockHeader1 = dataStructureUtil.randomSignedBeaconBlockHeader();
    final SignedBeaconBlockHeader blockHeader2 = dataStructureUtil.randomSignedBeaconBlockHeader();
    final Bytes32 blockRoot1 = blockHeader1.getMessage().getRoot();
    final Bytes32 blockRoot2 = blockHeader2.getMessage().getRoot();
    final DataColumnSidecar sidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSidecar sidecar1_1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ONE);
    final DataColumnSidecar sidecar1_3 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, UInt64.valueOf(3));
    final DataColumnSidecar sidecar2_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);
    when(batchRpc.requestDataColumnSidecarsByRange(
            UInt256.ZERO, blockHeader1.getMessage().getSlot(), 1, List.of(ZERO, ONE)))
        .thenReturn(AsyncStream.of(sidecar1_0, sidecar1_1));
    when(batchRpc.requestDataColumnSidecarsByRange(
            UInt256.ONE, blockHeader1.getMessage().getSlot(), 1, List.of(UInt64.valueOf(3))))
        .thenReturn(AsyncStream.of(sidecar1_3));
    when(batchRpc.requestDataColumnSidecarsByRange(
            UInt256.valueOf(2), blockHeader2.getMessage().getSlot(), 1, List.of(ZERO)))
        .thenReturn(AsyncStream.of(sidecar2_0));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO,
            new DataColumnSlotAndIdentifier(blockHeader1.getMessage().getSlot(), blockRoot1, ZERO));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO,
            new DataColumnSlotAndIdentifier(blockHeader1.getMessage().getSlot(), blockRoot1, ONE));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_3_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ONE,
            new DataColumnSlotAndIdentifier(
                blockHeader1.getMessage().getSlot(), blockRoot1, UInt64.valueOf(3)));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar2_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.valueOf(2),
            new DataColumnSlotAndIdentifier(blockHeader2.getMessage().getSlot(), blockRoot2, ZERO));
    dataColumnReqResp.flush();

    assertThat(dataColumnSidecar1_0_Future).isCompletedWithValue(sidecar1_0);
    assertThat(dataColumnSidecar1_1_Future).isCompletedWithValue(sidecar1_1);
    assertThat(dataColumnSidecar1_3_Future).isCompletedWithValue(sidecar1_3);
    assertThat(dataColumnSidecar2_0_Future).isCompletedWithValue(sidecar2_0);
    verify(batchRpc)
        .requestDataColumnSidecarsByRange(
            UInt256.ZERO, blockHeader1.getMessage().getSlot(), 1, List.of(ZERO, ONE));
    verify(batchRpc)
        .requestDataColumnSidecarsByRange(
            UInt256.ONE, blockHeader1.getMessage().getSlot(), 1, List.of(UInt64.valueOf(3)));
    verify(batchRpc)
        .requestDataColumnSidecarsByRange(
            UInt256.valueOf(2), blockHeader2.getMessage().getSlot(), 1, List.of(ZERO));
  }

  @Test
  @SuppressWarnings("JavaCase")
  public void formsChains() {
    final SignedBeaconBlockHeader blockHeader1 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(1));
    final SignedBeaconBlockHeader blockHeader2 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(2));
    final SignedBeaconBlockHeader blockHeader3 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(3));
    final SignedBeaconBlockHeader blockHeader4 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(4));
    final SignedBeaconBlockHeader blockHeader5 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(5));

    final DataColumnSidecar sidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSidecar sidecar1_1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ONE);
    final DataColumnSidecar sidecar2_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);
    final DataColumnSidecar sidecar2_1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ONE);
    final DataColumnSidecar sidecar3_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader3, ZERO);
    final DataColumnSidecar sidecar3_1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader3, ONE);
    final DataColumnSidecar sidecar4_1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader4, ONE);
    final DataColumnSidecar sidecar5_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader5, ZERO);
    final DataColumnSidecar sidecar5_1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader5, ONE);

    when(batchRpc.requestDataColumnSidecarsByRange(
            UInt256.ZERO, blockHeader1.getMessage().getSlot(), 3, List.of(ZERO, ONE)))
        .thenReturn(
            AsyncStream.of(sidecar1_0, sidecar1_1, sidecar2_0, sidecar2_1, sidecar3_0, sidecar3_1));
    when(batchRpc.requestDataColumnSidecarsByRange(
            UInt256.ZERO, blockHeader4.getMessage().getSlot(), 1, List.of(ONE)))
        .thenReturn(AsyncStream.of(sidecar4_1));
    when(batchRpc.requestDataColumnSidecarsByRange(
            UInt256.ONE, blockHeader5.getMessage().getSlot(), 1, List.of(ZERO, ONE)))
        .thenReturn(AsyncStream.of(sidecar5_0, sidecar5_1));

    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, DataColumnSlotAndIdentifier.fromDataColumn(sidecar1_0));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, DataColumnSlotAndIdentifier.fromDataColumn(sidecar1_1));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar2_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, DataColumnSlotAndIdentifier.fromDataColumn(sidecar2_0));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar2_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, DataColumnSlotAndIdentifier.fromDataColumn(sidecar2_1));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar3_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, DataColumnSlotAndIdentifier.fromDataColumn(sidecar3_0));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar3_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, DataColumnSlotAndIdentifier.fromDataColumn(sidecar3_1));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar4_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, DataColumnSlotAndIdentifier.fromDataColumn(sidecar4_1));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar5_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ONE, DataColumnSlotAndIdentifier.fromDataColumn(sidecar5_0));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar5_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ONE, DataColumnSlotAndIdentifier.fromDataColumn(sidecar5_1));

    dataColumnReqResp.flush();

    assertThat(dataColumnSidecar1_0_Future).isCompletedWithValue(sidecar1_0);
    assertThat(dataColumnSidecar1_1_Future).isCompletedWithValue(sidecar1_1);
    assertThat(dataColumnSidecar2_0_Future).isCompletedWithValue(sidecar2_0);
    assertThat(dataColumnSidecar2_1_Future).isCompletedWithValue(sidecar2_1);
    assertThat(dataColumnSidecar3_0_Future).isCompletedWithValue(sidecar3_0);
    assertThat(dataColumnSidecar3_1_Future).isCompletedWithValue(sidecar3_1);
    assertThat(dataColumnSidecar4_1_Future).isCompletedWithValue(sidecar4_1);
    assertThat(dataColumnSidecar5_0_Future).isCompletedWithValue(sidecar5_0);
    assertThat(dataColumnSidecar5_1_Future).isCompletedWithValue(sidecar5_1);

    verify(batchRpc)
        .requestDataColumnSidecarsByRange(
            UInt256.ZERO, blockHeader1.getMessage().getSlot(), 3, List.of(ZERO, ONE));
    verify(batchRpc)
        .requestDataColumnSidecarsByRange(
            UInt256.ZERO, blockHeader4.getMessage().getSlot(), 1, List.of(ONE));
    verify(batchRpc)
        .requestDataColumnSidecarsByRange(
            UInt256.ONE, blockHeader5.getMessage().getSlot(), 1, List.of(ZERO, ONE));
  }
}
