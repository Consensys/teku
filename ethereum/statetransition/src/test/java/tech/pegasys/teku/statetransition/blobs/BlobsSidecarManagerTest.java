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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarAndValidationResult.NOT_REQUIRED;
import static tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarAndValidationResult.validResult;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.ForkSchedule;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.BlobSidecarValidator;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobsSidecarManagerTest {
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
  private final BlobsSidecarManagerImpl blobsSidecarManager =
      new BlobsSidecarManagerImpl(
          mockedSpec,
          recentChainData,
          blobSidecarValidator,
          storageQueryChannel,
          storageUpdateChannel);

  @BeforeEach
  void setUp() {
    when(storageUpdateChannel.onBlobsSidecar(any())).thenReturn(SafeFuture.COMPLETE);
    when(mockedSpec.atSlot(any())).thenReturn(mockedSpecVersion);
    when(mockedSpecVersion.miscHelpers()).thenReturn(mockedMiscHelpers);
    when(mockedSpec.getForkSchedule()).thenReturn(mockedForkSchedule);
  }

  @Test
  void shouldStoreUnconfirmedValidatedBlobsSidecar() {
    final BlobsSidecar blobsSidecar = dataStructureUtil.randomBlobsSidecar();
    blobsSidecarManager.storeUnconfirmedValidatedBlobsSidecar(blobsSidecar);

    verify(storageUpdateChannel).onBlobsSidecar(blobsSidecar);
  }

  @Test
  void shouldStoreUnconfirmedBlobsSidecar() {
    final BlobsSidecar blobsSidecar = dataStructureUtil.randomBlobsSidecar();
    blobsSidecarManager.storeUnconfirmedBlobsSidecar(blobsSidecar);

    verify(storageUpdateChannel).onBlobsSidecar(blobsSidecar);
  }

  @Test
  void shouldCreatePreValidatedAvailabilityChecker() {
    final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
        dataStructureUtil.randomConsistentSignedBeaconBlockAndBlobsSidecar();

    blobsSidecarManager.storeUnconfirmedValidatedBlobsSidecar(
        blockAndBlobsSidecar.getBlobsSidecar());

    final BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker =
        blobsSidecarManager.createAvailabilityChecker(blockAndBlobsSidecar.getSignedBeaconBlock());

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .isCompletedWithValue(validResult(blockAndBlobsSidecar.getBlobsSidecar()));

    verify(mockedMiscHelpers, never()).isDataAvailable(any(), any(), any(), any());
  }

  @Test
  void shouldCreateNotRequiredCheckerIfBlockCommitmentsIsEmpty() {
    final SignedBeaconBlock blockWithEmptyCommitments =
        dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();

    final BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker =
        blobsSidecarManager.createAvailabilityChecker(blockWithEmptyCommitments);

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .isCompletedWithValue(NOT_REQUIRED);

    verify(mockedMiscHelpers, never()).isDataAvailable(any(), any(), any(), any());
  }

  @Test
  void shouldCreateValidatingAvailabilityChecker() {
    final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
        dataStructureUtil.randomConsistentSignedBeaconBlockAndBlobsSidecar();

    blobsSidecarManager.storeUnconfirmedBlobsSidecar(blockAndBlobsSidecar.getBlobsSidecar());

    final BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker =
        blobsSidecarManager.createAvailabilityChecker(blockAndBlobsSidecar.getSignedBeaconBlock());

    when(storageQueryChannel.getBlobsSidecar(any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(blockAndBlobsSidecar.getBlobsSidecar())));

    // is available
    when(mockedMiscHelpers.isDataAvailable(any(), any(), any(), any())).thenReturn(true);

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    assertThat(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .isCompletedWithValue(validResult(blockAndBlobsSidecar.getBlobsSidecar()));

    verify(mockedMiscHelpers).isDataAvailable(any(), any(), any(), any());
  }

  @Test
  void shouldDiscardCachedValidatedBlobsOnSlot() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

    final BlobsSidecar blobs1 = dataStructureUtil.randomBlobsSidecar(blockRoot, UInt64.ONE);
    final BlobsSidecar blobs2 = dataStructureUtil.randomBlobsSidecar(blockRoot, UInt64.valueOf(2));

    blobsSidecarManager.storeUnconfirmedValidatedBlobsSidecar(blobs1);
    blobsSidecarManager.storeUnconfirmedValidatedBlobsSidecar(blobs2);

    assertThat(blobsSidecarManager.getValidatedPendingBlobsForSlot(UInt64.ONE))
        .containsEntry(blockRoot, blobs1);
    assertThat(blobsSidecarManager.getValidatedPendingBlobsForSlot(UInt64.valueOf(2)))
        .containsEntry(blockRoot, blobs2);

    blobsSidecarManager.onSlot(UInt64.valueOf(2));

    assertThat(blobsSidecarManager.getValidatedPendingBlobsForSlot(UInt64.ONE))
        .containsEntry(blockRoot, blobs1);
    assertThat(blobsSidecarManager.getValidatedPendingBlobsForSlot(UInt64.valueOf(2)))
        .containsEntry(blockRoot, blobs2);

    blobsSidecarManager.onSlot(UInt64.valueOf(4));

    assertThat(blobsSidecarManager.getValidatedPendingBlobsForSlot(UInt64.ONE)).isEmpty();
    assertThat(blobsSidecarManager.getValidatedPendingBlobsForSlot(UInt64.valueOf(2))).isEmpty();
  }

  @Test
  void isStorageOfBlobsSidecarRequired_shouldReturnTrueIfWithinRange() {
    when(mockedForkSchedule.getSpecMilestoneAtSlot(any())).thenReturn(SpecMilestone.DENEB);

    final UInt64 currentEpoch = UInt64.valueOf(10000);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    // 10000 - 8000 < 4096
    final UInt64 slot = dataStructureUtil.randomUInt64();
    when(mockedSpec.computeEpochAtSlot(slot)).thenReturn(UInt64.valueOf(8000));

    final boolean result = blobsSidecarManager.isStorageOfBlobsSidecarRequired(slot);

    assertThat(result).isTrue();
  }

  @Test
  void isStorageOfBlobsSidecarRequired_shouldReturnFalseIfNotWithinRange() {
    when(mockedForkSchedule.getSpecMilestoneAtSlot(any())).thenReturn(SpecMilestone.DENEB);

    final UInt64 currentEpoch = UInt64.valueOf(10000);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    // 10000 - 4000 > 4096
    final UInt64 slot = dataStructureUtil.randomUInt64();
    when(mockedSpec.computeEpochAtSlot(slot)).thenReturn(UInt64.valueOf(4000));

    final boolean result = blobsSidecarManager.isStorageOfBlobsSidecarRequired(slot);

    assertThat(result).isFalse();
  }

  @Test
  void isStorageOfBlobsSidecarRequired_shouldReturnFalseIfPreDeneb() {
    when(mockedForkSchedule.getSpecMilestoneAtSlot(any())).thenReturn(SpecMilestone.BELLATRIX);

    final boolean result =
        blobsSidecarManager.isStorageOfBlobsSidecarRequired(dataStructureUtil.randomUInt64());

    assertThat(result).isFalse();
  }
}
