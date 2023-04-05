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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import tech.pegasys.teku.dataproviders.lookup.BlobSidecarsProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker.BlobSidecarsValidationResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceBlobSidecarsAvailabilityCheckerTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalDeneb());

  private final Spec spec = mock(Spec.class);
  private final SpecVersion specVersion = mock(SpecVersion.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final MiscHelpers miscHelpers = mock(MiscHelpers.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final BlobSidecarsProvider blobSidecarsProvider = mock(BlobSidecarsProvider.class);

  private SignedBeaconBlock block;
  private List<BlobSidecar> blobSidecars;

  private ForkChoiceBlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker;

  @BeforeEach
  void setUp() {
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(recentChainData.getStore()).thenReturn(store);
  }

  @Test
  void shouldReturnNotRequiredWhenBlockIsOutsideAvailabilityWindow() {
    prepareBlockAndBlobSidecarsOutsideAvailabilityWindow();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertNotRequired(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult());
    assertNotRequired(blobSidecarsAvailabilityChecker.validate(Collections.emptyList()));
  }

  @Test
  void shouldReturnNotRequiredWhenNumberOfKzgCommitmentsIsZero() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();
    prepareBlockAndBlobSidecarsInAvailabilityWindow(true, Optional.of(block));

    assertNotRequired(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult());
  }

  @Test
  void shouldReturnNotAvailable() {
    prepareBlockAndBlobSidecarsInAvailabilityWindow(false);

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertNotAvailable(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult());
    assertNotAvailable(blobSidecarsAvailabilityChecker.validate(Collections.emptyList()));
  }

  @Test
  void shouldReturnInvalid() {
    prepareBlockAndBlobSidecarsInAvailabilityWindow(true);

    when(miscHelpers.isDataAvailable(
            eq(block.getSlot()),
            eq(block.getRoot()),
            argThat(new KzgCommitmentsArgumentMatcher(block)),
            eq(blobSidecars)))
        .thenReturn(false);

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertInvalid(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult(), Optional.empty());
    assertInvalid(blobSidecarsAvailabilityChecker.validate(blobSidecars), Optional.empty());
  }

  @Test
  void shouldReturnInvalidDueToException() {
    prepareBlockAndBlobSidecarsInAvailabilityWindow(true);

    final IllegalStateException cause = new IllegalStateException("ops!");

    when(miscHelpers.isDataAvailable(
            eq(block.getSlot()),
            eq(block.getRoot()),
            argThat(new KzgCommitmentsArgumentMatcher(block)),
            eq(blobSidecars)))
        .thenThrow(cause);

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertInvalid(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult(), Optional.of(cause));
    assertInvalid(blobSidecarsAvailabilityChecker.validate(blobSidecars), Optional.of(cause));
  }

  @Test
  void shouldReturnValid() {
    prepareBlockAndBlobSidecarsInAvailabilityWindow(true);

    when(miscHelpers.isDataAvailable(
            eq(block.getSlot()),
            eq(block.getRoot()),
            argThat(new KzgCommitmentsArgumentMatcher(block)),
            eq(blobSidecars)))
        .thenReturn(true);

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();
    assertAvailable(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult());
    assertAvailable(blobSidecarsAvailabilityChecker.validate(blobSidecars));
  }

  @Test
  void shouldReturnNotRequiredWhenDataAvailabilityCheckNotInitiated() {
    prepareBlockAndBlobSidecarsInAvailabilityWindow(true);

    assertNotRequired(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult());
  }

  private void assertNotRequired(
      final SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isFailure(), "is not failure")
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            BlobSidecarsAndValidationResult::isNotRequired, "is not required")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().isEmpty(), "doesn't have blob sidecars");
  }

  private void assertInvalid(
      SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck,
      Optional<Throwable> cause) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.INVALID,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().equals(blobSidecars), "has blob sidecars")
        .isCompletedWithValueMatching(
            result -> result.getCause().equals(cause), "matches the cause");
  }

  private void assertNotAvailable(
      SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(BlobSidecarsAndValidationResult::isFailure, "is failure")
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.NOT_AVAILABLE,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().isEmpty(), "doesn't have blob sidecars");
  }

  private void assertAvailable(
      SafeFuture<BlobSidecarsAndValidationResult> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isFailure(), "is not failure")
        .isCompletedWithValueMatching(BlobSidecarsAndValidationResult::isValid, "is valid")
        .isCompletedWithValueMatching(
            result -> result.getValidationResult() == BlobSidecarsValidationResult.VALID,
            "is valid")
        .isCompletedWithValueMatching(
            result -> result.getBlobSidecars().equals(blobSidecars), "has blob sidecars");
  }

  private void prepareBlockAndBlobSidecarsInAvailabilityWindow(
      final boolean blobSidecarsAvailable) {
    prepareBlockAndBlobSidecarsInAvailabilityWindow(blobSidecarsAvailable, Optional.empty());
  }

  private void prepareBlockAndBlobSidecarsInAvailabilityWindow(
      final boolean blobSidecarsAvailable, final Optional<SignedBeaconBlock> providedBlock) {
    block = providedBlock.orElse(dataStructureUtil.randomSignedBeaconBlock());
    blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(true);

    if (blobSidecarsAvailable) {
      when(blobSidecarsProvider.getBlobSidecars(
              new SlotAndBlockRoot(block.getSlot(), block.getRoot())))
          .thenReturn(SafeFuture.completedFuture(blobSidecars));
      when(blobSidecarsProvider.getBlobSidecars(block))
          .thenReturn(SafeFuture.completedFuture(blobSidecars));
    } else {
      when(blobSidecarsProvider.getBlobSidecars(
              new SlotAndBlockRoot(block.getSlot(), block.getRoot())))
          .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
      when(blobSidecarsProvider.getBlobSidecars(block))
          .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    }

    blobSidecarsAvailabilityChecker =
        new ForkChoiceBlobSidecarsAvailabilityChecker(
            spec, specVersion, recentChainData, block, blobSidecarsProvider);
  }

  private void prepareBlockAndBlobSidecarsOutsideAvailabilityWindow() {
    block = dataStructureUtil.randomSignedBeaconBlock();

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(false);
    when(blobSidecarsProvider.getBlobSidecars(
            new SlotAndBlockRoot(block.getSlot(), block.getRoot())))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    when(blobSidecarsProvider.getBlobSidecars(block))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    blobSidecarsAvailabilityChecker =
        new ForkChoiceBlobSidecarsAvailabilityChecker(
            spec, specVersion, recentChainData, block, blobSidecarsProvider);
  }

  private static class KzgCommitmentsArgumentMatcher
      implements ArgumentMatcher<List<KZGCommitment>> {

    private final SignedBeaconBlock block;

    private KzgCommitmentsArgumentMatcher(final SignedBeaconBlock block) {
      this.block = block;
    }

    @Override
    public boolean matches(final List<KZGCommitment> argument) {
      return block
          .getMessage()
          .getBody()
          .toVersionDeneb()
          .orElseThrow()
          .getBlobKzgCommitments()
          .stream()
          .map(SszKZGCommitment::getKZGCommitment)
          .collect(Collectors.toUnmodifiableList())
          .equals(argument);
    }
  }
}
