package tech.pegasys.teku.statetransition.forkchoice;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataColumnSidecarAvailabilityCheckerTest {

    private final KZG kzg = mock(KZG.class);

    private final Spec spec = mock(Spec.class);

    private final SignedBeaconBlock block = mock(SignedBeaconBlock.class);

    private final BeaconBlock beaconBlock = mock(BeaconBlock.class);
    final DataAvailabilitySampler das = mock(DataAvailabilitySampler.class);

    private DataColumnSidecarAvailabilityChecker checker;

    @BeforeEach
    void setup(){
        checker = new DataColumnSidecarAvailabilityChecker(das,kzg,spec,block);
        when(block.getMessage()).thenReturn(beaconBlock);
    }

    @Test
    void shouldReturnNotRequiredWhenBeforeFulu() throws ExecutionException, InterruptedException {

        when(das.checkSamplingEligibility(block.getMessage()))
                .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_BEFORE_FULU);
        assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
        assertThat(checker.getAvailabilityCheckResult().get()).isEqualTo(
                DataAndValidationResult.notRequired());

    }

    @Test
    void shouldReturnNotRequiredWhenOldEpoch() throws ExecutionException, InterruptedException {


        when(das.checkSamplingEligibility(block.getMessage()))
                .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH);
            assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
        assertThat(checker.getAvailabilityCheckResult().get()).isEqualTo(
                DataAndValidationResult.notRequired());

    }

    @Test
    void shouldReturnNotRequiredWhenNoBlobsInTheBlock() throws ExecutionException, InterruptedException {


        when(das.checkSamplingEligibility(block.getMessage()))
                .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS);
        assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
        assertThat(checker.getAvailabilityCheckResult().get()).isEqualTo(
                DataAndValidationResult.notRequired());

    }

    @Test
    void shouldReturnInvalidWhenDASSamplerReturnEmptyList() throws ExecutionException, InterruptedException {
        when(das.checkSamplingEligibility(block.getMessage()))
                .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
        when(das.checkDataAvailability(any(),any())).thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
        assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
        assertThat(checker.getAvailabilityCheckResult().get()).isEqualTo(
                DataAndValidationResult.notAvailable()
        );
    }

    @Test
    void shouldReturnInvalidWhenDASSamplerReturnError() throws ExecutionException, InterruptedException {
        when(das.checkSamplingEligibility(block.getMessage()))
                .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
        when(das.checkDataAvailability(any(),any())).thenReturn(SafeFuture.failedFuture(new RuntimeException("Error during DAS check")));
        assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
        assertThat(checker.getAvailabilityCheckResult().get()).isEqualTo(
                DataAndValidationResult.notAvailable()
        );
    }

    @Test
    void shouldReturnValidWhenDASSamplerReturnListWithIndices() throws ExecutionException, InterruptedException {
        List<UInt64> listOfIndices = Lists.newArrayList(UInt64.valueOf(1), UInt64.valueOf(2));
        when(das.checkSamplingEligibility(block.getMessage()))
                .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
        when(das.checkDataAvailability(any(),any())).thenReturn(SafeFuture.completedFuture(listOfIndices));
        assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
        assertThat(checker.getAvailabilityCheckResult().get()).isEqualTo(
                DataAndValidationResult.validResult(listOfIndices)
        );
    }

}