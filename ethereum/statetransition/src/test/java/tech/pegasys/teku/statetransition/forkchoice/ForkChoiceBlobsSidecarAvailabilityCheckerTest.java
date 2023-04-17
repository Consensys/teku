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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.lookup.BlobsSidecarProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarValidationResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceBlobsSidecarAvailabilityCheckerTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final SpecVersion specVersionMock = mock(SpecVersion.class);
  private final BlobsSidecarProvider blobsProvider = mock(BlobsSidecarProvider.class);
  private final MiscHelpers miscHelpers = mock(MiscHelpers.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final long availabilityWindow =
      (long) spec.getGenesisSpec().getSlotsPerEpoch() * MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

  private BlobsSidecar blobsSidecar;
  private BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker;

  @BeforeEach
  void setUp() {
    when(specVersionMock.getSlotsPerEpoch()).thenReturn(spec.getGenesisSpec().getSlotsPerEpoch());
    when(specVersionMock.miscHelpers()).thenReturn(miscHelpers);
  }

  @Test
  void shouldReturnNotRequired() {
    prepareBlockAndBlobOutsideAvailabilityWindow();

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertNotRequired(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult());
    assertNotRequired(blobsSidecarAvailabilityChecker.validate(Optional.empty()));
  }

  @Test
  void shouldReturnNotAvailable() {
    prepareBlockAndBlobInAvailabilityWindow(false);

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertNotAvailable(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult());
    assertNotAvailable(blobsSidecarAvailabilityChecker.validate(Optional.empty()));
  }

  @Test
  void shouldReturnInvalid() {
    prepareBlockAndBlobInAvailabilityWindow(true);

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertInvalid(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult(), Optional.empty());
    assertInvalid(
        blobsSidecarAvailabilityChecker.validate(Optional.of(blobsSidecar)), Optional.empty());
  }

  @Test
  void shouldReturnInvalidDueToException() {
    prepareBlockAndBlobInAvailabilityWindow(true);

    final Throwable cause = new IllegalStateException("ops!");

    when(miscHelpers.isDataAvailable(
            eq(blobsSidecar.getBeaconBlockSlot()),
            eq(blobsSidecar.getBeaconBlockRoot()),
            any(),
            eq(blobsSidecar)))
        .thenThrow(cause);

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertInvalid(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult(), Optional.of(cause));
    assertInvalid(
        blobsSidecarAvailabilityChecker.validate(Optional.of(blobsSidecar)), Optional.of(cause));
  }

  @Test
  void shouldReturnValid() {
    prepareBlockAndBlobInAvailabilityWindow(true);

    when(miscHelpers.isDataAvailable(
            eq(blobsSidecar.getBeaconBlockSlot()),
            eq(blobsSidecar.getBeaconBlockRoot()),
            any(),
            eq(blobsSidecar)))
        .thenReturn(true);

    assertThat(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertAvailable(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult());
    assertAvailable(blobsSidecarAvailabilityChecker.validate(Optional.of(blobsSidecar)));
  }

  @Test
  void shouldReturnNotRequiredWhenNotDataAvailabilityCheckNotInitiated() {
    prepareBlockAndBlobInAvailabilityWindow(true);

    assertNotRequired(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult());
  }

  private void assertNotRequired(
      SafeFuture<BlobsSidecarAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isFailure(), "is not failure")
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            BlobsSidecarAndValidationResult::isNotRequired, "is not required")
        .isCompletedWithValueMatching(
            result -> result.getBlobsSidecar().isEmpty(), "has empty blob");
  }

  private void assertInvalid(
      SafeFuture<BlobsSidecarAndValidationResult> availabilityOrValidityCheck,
      Optional<Throwable> cause) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobsSidecarValidationResult.INVALID,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.getBlobsSidecar().orElseThrow().equals(blobsSidecar),
            "has not empty blob")
        .isCompletedWithValueMatching(
            result -> result.getCause().equals(cause), "doesn't match the cause");
  }

  private void assertNotAvailable(
      SafeFuture<BlobsSidecarAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(BlobsSidecarAndValidationResult::isFailure, "is failure")
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobsSidecarValidationResult.NOT_AVAILABLE,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.getBlobsSidecar().isEmpty(), "has empty blob");
  }

  private void assertAvailable(
      SafeFuture<BlobsSidecarAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isFailure(), "is not failure")
        .isCompletedWithValueMatching(BlobsSidecarAndValidationResult::isValid, "is valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobsSidecarValidationResult.VALID,
            "is valid")
        .isCompletedWithValueMatching(
            result -> result.getBlobsSidecar().orElseThrow().equals(blobsSidecar),
            "has not empty blob");
  }

  private void prepareBlockAndBlobInAvailabilityWindow(boolean blobAvailable) {
    when(recentChainData.getCurrentSlot())
        .thenReturn(Optional.of(UInt64.valueOf(availabilityWindow)));

    final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
        dataStructureUtil.randomConsistentSignedBeaconBlockAndBlobsSidecar(UInt64.ONE);

    blobsSidecar = blockAndBlobsSidecar.getBlobsSidecar();

    if (blobAvailable) {
      when(blobsProvider.getBlobsSidecar(any(SlotAndBlockRoot.class)))
          .thenReturn(SafeFuture.completedFuture(Optional.of(blobsSidecar)));
      when(blobsProvider.getBlobsSidecar(any(SignedBeaconBlock.class)))
          .thenReturn(SafeFuture.completedFuture(Optional.of(blobsSidecar)));
    } else {
      when(blobsProvider.getBlobsSidecar(any(SlotAndBlockRoot.class)))
          .thenReturn(SafeFuture.completedFuture(Optional.empty()));
      when(blobsProvider.getBlobsSidecar(any(SignedBeaconBlock.class)))
          .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    }

    blobsSidecarAvailabilityChecker =
        new ForkChoiceBlobsSidecarAvailabilityChecker(
            specVersionMock,
            recentChainData,
            blockAndBlobsSidecar.getSignedBeaconBlock(),
            blobsProvider);
  }

  private void prepareBlockAndBlobOutsideAvailabilityWindow() {
    when(recentChainData.getCurrentSlot())
        .thenReturn(Optional.of(UInt64.valueOf(availabilityWindow + 2)));

    final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
        dataStructureUtil.randomConsistentSignedBeaconBlockAndBlobsSidecar(UInt64.ONE);

    blobsSidecar = blockAndBlobsSidecar.getBlobsSidecar();

    when(blobsProvider.getBlobsSidecar(any(SlotAndBlockRoot.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(blobsProvider.getBlobsSidecar(any(SignedBeaconBlock.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    blobsSidecarAvailabilityChecker =
        new ForkChoiceBlobsSidecarAvailabilityChecker(
            specVersionMock,
            recentChainData,
            blockAndBlobsSidecar.getSignedBeaconBlock(),
            blobsProvider);
  }
}
