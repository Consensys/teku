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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
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
      new DataColumnReqRespBatchingImpl(byRootRpc, byRootSchema, spec);

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
            UInt256.ZERO, List.of(byRootSchema.create(blockRoot1, List.of(ZERO, ONE))), Map.of()))
        .thenReturn(AsyncStream.of(sidecar10_0, sidecar10_1));
    when(byRootRpc.requestDataColumnSidecarsByRoot(
            UInt256.ONE,
            List.of(byRootSchema.create(blockRoot1, List.of(UInt64.valueOf(3)))),
            Map.of()))
        .thenReturn(AsyncStream.of(sidecar10_3));
    when(byRootRpc.requestDataColumnSidecarsByRoot(
            UInt256.valueOf(2), List.of(byRootSchema.create(blockRoot2, List.of(ZERO))), Map.of()))
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
            UInt256.ZERO, List.of(byRootSchema.create(blockRoot1, List.of(ZERO, ONE))), Map.of());
    verify(byRootRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.ONE,
            List.of(byRootSchema.create(blockRoot1, List.of(UInt64.valueOf(3)))),
            Map.of());
    verify(byRootRpc)
        .requestDataColumnSidecarsByRoot(
            UInt256.valueOf(2), List.of(byRootSchema.create(blockRoot2, List.of(ZERO))), Map.of());
  }

  @Test
  @SuppressWarnings({"JavaCase", "unchecked"})
  public void shouldPassBlobKzgCommitmentsByRootToByRootRpc() {
    final SignedBeaconBlockHeader blockHeader10 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(10));
    final Bytes32 blockRoot1 = blockHeader10.getMessage().getRoot();
    final DataColumnSidecar sidecar10_0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader10, ZERO);
    final SszList<SszKZGCommitment> blobKzgCommitments = mock(SszList.class);
    final List<DataColumnsByRootIdentifier> expectedIdentifiers =
        List.of(byRootSchema.create(blockRoot1, List.of(ZERO)));
    final Map<Bytes32, SszList<SszKZGCommitment>> expectedCommitments =
        Map.of(blockRoot1, blobKzgCommitments);

    when(byRootRpc.requestDataColumnSidecarsByRoot(
            UInt256.ZERO, expectedIdentifiers, expectedCommitments))
        .thenReturn(AsyncStream.of(sidecar10_0));

    final SafeFuture<DataColumnSidecar> dataColumnSidecar10_0_Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO,
            new DataColumnSlotAndIdentifier(blockHeader10.getMessage().getSlot(), blockRoot1, ZERO),
            Optional.of(blobKzgCommitments));

    dataColumnReqResp.flush();

    assertThat(dataColumnSidecar10_0_Future).isCompletedWithValue(sidecar10_0);
    verify(byRootRpc)
        .requestDataColumnSidecarsByRoot(UInt256.ZERO, expectedIdentifiers, expectedCommitments);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldFailRequestsWhenSameRootHasConflictingBlobKzgCommitments() {
    final SignedBeaconBlockHeader blockHeader10 =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(10));
    final Bytes32 blockRoot1 = blockHeader10.getMessage().getRoot();
    final SszList<SszKZGCommitment> blobKzgCommitments1 = mock(SszList.class);
    final SszList<SszKZGCommitment> blobKzgCommitments2 = mock(SszList.class);

    final SafeFuture<DataColumnSidecar> dataColumnSidecar100Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO,
            new DataColumnSlotAndIdentifier(blockHeader10.getMessage().getSlot(), blockRoot1, ZERO),
            Optional.of(blobKzgCommitments1));
    final SafeFuture<DataColumnSidecar> dataColumnSidecar101Future =
        dataColumnReqResp.requestDataColumnSidecar(
            UInt256.ZERO,
            new DataColumnSlotAndIdentifier(blockHeader10.getMessage().getSlot(), blockRoot1, ONE),
            Optional.of(blobKzgCommitments2));

    dataColumnReqResp.flush();

    assertThat(dataColumnSidecar100Future).isCompletedExceptionally();
    assertThat(dataColumnSidecar101Future).isCompletedExceptionally();
    verifyNoInteractions(byRootRpc);
  }

  @Test
  public void partitionRequests() {
    final List<UInt64> indices128 = Stream.iterate(ZERO, UInt64::increment).limit(128).toList();

    final List<DataColumnsByRootIdentifier> byRootIdentifiers = new ArrayList<>();
    byRootIdentifiers.add(
        new DataColumnsByRootIdentifier(
            dataStructureUtil.randomBytes32(), indices128, byRootSchema));
    final List<List<DataColumnsByRootIdentifier>> batchesSingle =
        dataColumnReqResp.partitionRequests(byRootIdentifiers, 1000);
    assertThat(batchesSingle.getFirst().getFirst()).isEqualTo(byRootIdentifiers.getFirst());

    for (int i = 0; i < 9; i++) {
      byRootIdentifiers.add(
          new DataColumnsByRootIdentifier(
              dataStructureUtil.randomBytes32(), indices128, byRootSchema));
    }

    final List<List<DataColumnsByRootIdentifier>> batchesMinor =
        dataColumnReqResp.partitionRequests(byRootIdentifiers, 55);
    assertThat(batchesMinor).hasSize(10);
    for (int i = 0; i < batchesMinor.size(); i++) {
      assertThat(batchesMinor.get(i).getFirst()).isEqualTo(byRootIdentifiers.get(i));
    }

    final List<List<DataColumnsByRootIdentifier>> batchesMayor =
        dataColumnReqResp.partitionRequests(byRootIdentifiers, 640);
    assertThat(batchesMayor).hasSize(2);
    for (int i = 0; i < 5; i++) {
      assertThat(batchesMayor.getFirst().get(i)).isEqualTo(byRootIdentifiers.get(i));
    }
    for (int i = 0; i < 5; i++) {
      assertThat(batchesMayor.get(1).get(i)).isEqualTo(byRootIdentifiers.get(5 + i));
    }
  }
}
