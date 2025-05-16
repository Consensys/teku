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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnReqRespBatchingImplTest {
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final BatchDataColumnsByRootReqResp batchRpc = mock(BatchDataColumnsByRootReqResp.class);
  final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
  final DataColumnReqRespBatchingImpl dataColumnReqResp =
      new DataColumnReqRespBatchingImpl(batchRpc, schemaDefinitionsFulu);

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
    when(batchRpc.requestDataColumnSidecarsByRoot(
            UInt256.ZERO,
            List.of(
                schemaDefinitionsFulu
                    .getDataColumnsByRootIdentifierSchema()
                    .create(blockRoot1, List.of(ZERO, ONE)))))
        .thenReturn(AsyncStream.of(sidecar1_0, sidecar1_1));
    when(batchRpc.requestDataColumnSidecarsByRoot(
            UInt256.ONE,
            List.of(
                schemaDefinitionsFulu
                    .getDataColumnsByRootIdentifierSchema()
                    .create(blockRoot1, List.of(UInt64.valueOf(3))))))
        .thenReturn(AsyncStream.of(sidecar1_3));
    when(batchRpc.requestDataColumnSidecarsByRoot(
            UInt256.valueOf(2),
            List.of(
                schemaDefinitionsFulu
                    .getDataColumnsByRootIdentifierSchema()
                    .create(blockRoot2, List.of(ZERO)))))
        .thenReturn(AsyncStream.of(sidecar2_0));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, new DataColumnIdentifier(blockRoot1, ZERO));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_1_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO, new DataColumnIdentifier(blockRoot1, ONE));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar1_3_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ONE, new DataColumnIdentifier(blockRoot1, UInt64.valueOf(3)));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar2_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.valueOf(2), new DataColumnIdentifier(blockRoot2, ZERO));
    dataColumnReqResp.flush();

    assertThat(dataColumnSidecar1_0_Future).isCompletedWithValue(sidecar1_0);
    assertThat(dataColumnSidecar1_1_Future).isCompletedWithValue(sidecar1_1);
    assertThat(dataColumnSidecar1_3_Future).isCompletedWithValue(sidecar1_3);
    assertThat(dataColumnSidecar2_0_Future).isCompletedWithValue(sidecar2_0);
    verify(batchRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.ZERO,
            List.of(
                schemaDefinitionsFulu
                    .getDataColumnsByRootIdentifierSchema()
                    .create(blockRoot1, List.of(ZERO, ONE))));
    verify(batchRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.ONE,
            List.of(
                schemaDefinitionsFulu
                    .getDataColumnsByRootIdentifierSchema()
                    .create(blockRoot1, List.of(UInt64.valueOf(3)))));
    verify(batchRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.valueOf(2),
            List.of(
                schemaDefinitionsFulu
                    .getDataColumnsByRootIdentifierSchema()
                    .create(blockRoot2, List.of(ZERO))));
  }
}
