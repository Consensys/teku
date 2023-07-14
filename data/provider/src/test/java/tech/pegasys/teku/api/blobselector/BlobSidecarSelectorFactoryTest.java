/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.api.blobselector;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarSelectorFactoryTest {

  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final List<UInt64> indices = List.of(UInt64.ZERO, UInt64.ONE);
  private final SignedBeaconBlock block = data.randomSignedBeaconBlock();
  private final List<BlobSidecar> blobSidecars = data.randomBlobSidecars(3);

  private final BlobSidecarSelectorFactory blobSidecarSelectorFactory =
      new BlobSidecarSelectorFactory(client);

  @Test
  public void headSelector_shouldGetHeadBlobSidecars()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.getBlobSidecars(blockAndState.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<List<BlobSidecar>> result =
        blobSidecarSelectorFactory.headSelector().getBlobSidecars(indices).get();
    assertThat(result).hasValue(blobSidecars);
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedBlobSidecars()
      throws ExecutionException, InterruptedException {

    when(client.getFinalizedBlock()).thenReturn(Optional.of(block));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<List<BlobSidecar>> result =
        blobSidecarSelectorFactory.finalizedSelector().getBlobSidecars(indices).get();
    assertThat(result).hasValue(blobSidecars);
  }

  @Test
  public void genesisSelector_shouldGetBlobSidecarsForSlotZero()
      throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<List<BlobSidecar>> result =
        blobSidecarSelectorFactory.genesisSelector().getBlobSidecars(indices).get();
    assertThat(result).hasValue(blobSidecars);
  }

  @Test
  public void blockRootSelector_shouldGetBlobSidecarsByBlockRoot()
      throws ExecutionException, InterruptedException {
    when(client.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<List<BlobSidecar>> result =
        blobSidecarSelectorFactory
            .blockRootSelector(block.getRoot())
            .getBlobSidecars(indices)
            .get();
    assertThat(result).hasValue(blobSidecars);
  }

  @Test
  public void slotSelector_shouldGetBlobSidecarsAtSlot()
      throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<List<BlobSidecar>> result =
        blobSidecarSelectorFactory.slotSelector(block.getSlot()).getBlobSidecars(indices).get();
    assertThat(result).hasValue(blobSidecars);
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class, () -> blobSidecarSelectorFactory.createSelectorForBlockId("a"));
  }

  @Test
  public void stateRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> blobSidecarSelectorFactory.stateRootSelector(data.randomBytes32()));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class,
        () -> blobSidecarSelectorFactory.createSelectorForBlockId("justified"));
  }

  @Test
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class, blobSidecarSelectorFactory::justifiedSelector);
  }
}
