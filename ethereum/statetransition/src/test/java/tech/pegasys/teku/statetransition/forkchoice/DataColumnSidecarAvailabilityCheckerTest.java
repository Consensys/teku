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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;

class DataColumnSidecarAvailabilityCheckerTest {
  private final Spec spec = mock(Spec.class);

  private final SignedBeaconBlock block = mock(SignedBeaconBlock.class);

  private final BeaconBlock beaconBlock = mock(BeaconBlock.class);
  final DataAvailabilitySampler das = mock(DataAvailabilitySampler.class);

  private DataColumnSidecarAvailabilityChecker checker;

  @BeforeEach
  void setup() {
    checker = new DataColumnSidecarAvailabilityChecker(das, spec, block);
    when(block.getMessage()).thenReturn(beaconBlock);
  }

  @Test
  void shouldReturnNotRequiredWhenBeforeFulu() throws ExecutionException, InterruptedException {

    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_BEFORE_FULU);
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notRequired());
  }

  @Test
  void shouldReturnNotRequiredWhenOldEpoch() throws ExecutionException, InterruptedException {

    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH);
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notRequired());
  }

  @Test
  void shouldReturnNotRequiredWhenNoBlobsInTheBlock()
      throws ExecutionException, InterruptedException {

    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS);
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notRequired());
  }

  @Test
  void shouldReturnInvalidWhenDASSamplerReturnError()
      throws ExecutionException, InterruptedException {
    final RuntimeException exception = new RuntimeException("Error during DAS check");
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(any(), any())).thenReturn(SafeFuture.failedFuture(exception));
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notAvailable(exception));
  }

  @Test
  void shouldReturnValidWhenDASSamplerReturnListWithIndices()
      throws ExecutionException, InterruptedException {
    List<UInt64> listOfIndices = Lists.newArrayList(UInt64.valueOf(1), UInt64.valueOf(2));
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(any(), any()))
        .thenReturn(SafeFuture.completedFuture(listOfIndices));
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.validResult(listOfIndices));
  }
}
