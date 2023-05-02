/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.blobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.ForkSchedule;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.BlobSidecarValidator;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarManagerTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Spec mockedSpec = mock(Spec.class);
  private final SpecVersion mockedSpecVersion = mock(SpecVersion.class);
  private final ForkSchedule mockedForkSchedule = mock(ForkSchedule.class);
  private final MiscHelpers mockedMiscHelpers = mock(MiscHelpers.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StorageQueryChannel storageQueryChannel = mock(StorageQueryChannel.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final BlobSidecarValidator blobSidecarValidator = mock(BlobSidecarValidator.class);
  private final BlobSidecarManagerImpl blobSidecarManager =
      new BlobSidecarManagerImpl(
          mockedSpec,
          recentChainData,
          blobSidecarValidator,
          storageQueryChannel,
          storageUpdateChannel);

  @BeforeEach
  void setUp() {
    when(storageUpdateChannel.onBlobSidecar(any())).thenReturn(SafeFuture.COMPLETE);
    when(storageUpdateChannel.onNoBlobsSlot(any())).thenReturn(SafeFuture.COMPLETE);
    when(mockedSpec.atSlot(any())).thenReturn(mockedSpecVersion);
    when(mockedSpecVersion.miscHelpers()).thenReturn(mockedMiscHelpers);
    when(mockedSpec.getForkSchedule()).thenReturn(mockedForkSchedule);
  }

  @Test
  void shouldStoreBlobSidecar() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    blobSidecarManager.storeBlobSidecar(blobSidecar);
    verify(storageUpdateChannel).onBlobSidecar(blobSidecar);
  }

  @Test
  void shouldStoreNoBlobsSlot() {
    final SlotAndBlockRoot noBlobsSlotAndRoot =
        new SlotAndBlockRoot(UInt64.valueOf(3), Bytes32.ZERO);
    blobSidecarManager.storeNoBlobsSlot(noBlobsSlotAndRoot);
    verify(storageUpdateChannel).onNoBlobsSlot(noBlobsSlotAndRoot);
  }
}
