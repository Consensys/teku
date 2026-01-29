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
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnReqRespBatchingImplTest {
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final BatchDataColumnsByRootReqResp byRootRpc = mock(BatchDataColumnsByRootReqResp.class);
  final DataColumnsByRootIdentifierSchema byRootSchema =
      SchemaDefinitionsFulu.required(spec.getGenesisSchemaDefinitions())
          .getDataColumnsByRootIdentifierSchema();
  final DataColumnReqRespBatchingImpl dataColumnReqResp =
      new DataColumnReqRespBatchingImpl(byRootRpc, byRootSchema);

  @Test
  @SuppressWarnings("JavaCase")
  public void shouldCallByRoot() {
    final SignedBeaconBlockHeader blockHeader10 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(10));
    final SignedBeaconBlockHeader blockHeader11 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(11));
    final Bytes32 blockRoot1 = blockHeader10.getMessage().getRoot();
    final Bytes32 blockRoot2 = blockHeader11.getMessage().getRoot();
    final DataColumnSidecar sidecar10_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader10, ZERO);
    final DataColumnSidecar sidecar10_1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader10, ONE);
    final DataColumnSidecar sidecar10_3 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader10, UInt64.valueOf(3));
    final DataColumnSidecar sidecar11_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader11, ZERO);
    when(byRootRpc.requestDataColumnSidecarsByRoot(
            UInt256.ZERO, List.of(byRootSchema.create(blockRoot1, List.of(ZERO, ONE)))))
        .thenReturn(AsyncStream.of(sidecar10_0, sidecar10_1));
    when(byRootRpc.requestDataColumnSidecarsByRoot(
            UInt256.ONE, List.of(byRootSchema.create(blockRoot1, List.of(UInt64.valueOf(3))))))
        .thenReturn(AsyncStream.of(sidecar10_3));
    when(byRootRpc.requestDataColumnSidecarsByRoot(
            UInt256.valueOf(2), List.of(byRootSchema.create(blockRoot2, List.of(ZERO)))))
        .thenReturn(AsyncStream.of(sidecar11_0));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar10_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO,
            new DataColumnSlotAndIdentifier(
                blockHeader10.getMessage().getSlot(), blockRoot1, ZERO));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar10_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO,
            new DataColumnSlotAndIdentifier(blockHeader10.getMessage().getSlot(), blockRoot1, ONE));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar10_3_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ONE,
            new DataColumnSlotAndIdentifier(
                blockHeader10.getMessage().getSlot(), blockRoot1, UInt64.valueOf(3)));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar11_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.valueOf(2),
            new DataColumnSlotAndIdentifier(
                blockHeader11.getMessage().getSlot(), blockRoot2, ZERO));
    dataColumnReqResp.flush();

    assertThat(dataColumnSidecar10_0_Future).isCompletedWithValue(sidecar10_0);
    assertThat(dataColumnSidecar10_1_Future).isCompletedWithValue(sidecar10_1);
    assertThat(dataColumnSidecar10_3_Future).isCompletedWithValue(sidecar10_3);
    assertThat(dataColumnSidecar11_0_Future).isCompletedWithValue(sidecar11_0);
    verify(byRootRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.ZERO, List.of(byRootSchema.create(blockRoot1, List.of(ZERO, ONE))));
    verify(byRootRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.ONE, List.of(byRootSchema.create(blockRoot1, List.of(UInt64.valueOf(3)))));
    verify(byRootRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.valueOf(2), List.of(byRootSchema.create(blockRoot2, List.of(ZERO))));
  }
}
