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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarValidationResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceBlobsSidecarAvailabilityCheckerTest {
  private final Spec spec = TestSpecFactory.createMainnetEip4844();
  private final SpecVersion specVersionMock = mock(SpecVersion.class);
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
    prepareBlockAndBlobOutsideAvailabilityWindow(false);

    blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck();
    assertThat(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .isCompletedWithValueMatching(
            result ->
                !result.isFailure()
                    && result.isNotRequired()
                    && result.getBlobsSidecar().isEmpty());
  }

  @Test
  void shouldReturnNotAvailable() {
    prepareBlockAndBlobInAvailabilityWindow(false);

    blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck();
    assertThat(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .isCompletedWithValueMatching(
            result ->
                result.isFailure()
                    && result.getValidationResult() == BlobsSidecarValidationResult.NOT_AVAILABLE
                    && result.getBlobsSidecar().isEmpty());
  }

  @Test
  void shouldReturnInvalid() {
    prepareBlockAndBlobInAvailabilityWindow(true);

    blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck();
    assertThat(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .isCompletedWithValueMatching(
            result ->
                result.isFailure()
                    && result.getValidationResult() == BlobsSidecarValidationResult.INVALID
                    && result.getBlobsSidecar().orElseThrow().equals(blobsSidecar));
  }

  @Test
  void shouldReturnInvalidDueToException() {
    prepareBlockAndBlobInAvailabilityWindow(true);

    when(miscHelpers.isDataAvailable(any(), any(), any(), eq(blobsSidecar)))
        .thenThrow(new RuntimeException("ops!"));

    blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck();
    assertThat(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .isCompletedWithValueMatching(
            result ->
                result.isFailure()
                    && result.getValidationResult() == BlobsSidecarValidationResult.INVALID
                    && result.getBlobsSidecar().orElseThrow().equals(blobsSidecar));
  }

  private void prepareBlockAndBlobInAvailabilityWindow(boolean blobAvailable) {
    when(recentChainData.getCurrentSlot())
        .thenReturn(Optional.of(UInt64.valueOf(availabilityWindow)));

    if (blobAvailable) {
      blobsSidecar = dataStructureUtil.randomBlobsSidecar();
    }
    blobsSidecarAvailabilityChecker =
        new ForkChoiceBlobsSidecarAvailabilityChecker(
            specVersionMock,
            recentChainData,
            dataStructureUtil.randomSignedBeaconBlock(1),
            blobAvailable ? Optional.of(blobsSidecar) : Optional.empty());
  }

  private void prepareBlockAndBlobOutsideAvailabilityWindow(boolean blobAvailable) {
    when(recentChainData.getCurrentSlot())
        .thenReturn(Optional.of(UInt64.valueOf(availabilityWindow + 2)));

    if (blobAvailable) {
      blobsSidecar = dataStructureUtil.randomBlobsSidecar();
    }
    blobsSidecarAvailabilityChecker =
        new ForkChoiceBlobsSidecarAvailabilityChecker(
            specVersionMock,
            recentChainData,
            dataStructureUtil.randomSignedBeaconBlock(1),
            blobAvailable ? Optional.of(blobsSidecar) : Optional.empty());
  }
}
