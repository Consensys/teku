package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.ForkSchedule;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.Fork;

class MinCustodyPeriodSlotCalculatorTest {

  private final Spec spec = mock(Spec.class);
  private final ForkSchedule forkSchedule = mock(ForkSchedule.class);
  private final Fork fuluFork = mock(Fork.class);
  private final SpecConfig specConfig = mock(SpecConfig.class);
  private final SpecConfigFulu specConfigFulu = mock(SpecConfigFulu.class);

  private static final UInt64 SLOTS_PER_EPOCH = UInt64.valueOf(32);
  private static final UInt64 FULU_ACTIVATION_EPOCH = UInt64.valueOf(100);
  private static final int CUSTODY_PERIOD_EPOCHS = 10;

  @BeforeEach
  void setUp() {
    when(spec.getForkSchedule()).thenReturn(forkSchedule);
    when(forkSchedule.getFork(SpecMilestone.FULU)).thenReturn(fuluFork);
    when(fuluFork.getEpoch()).thenReturn(FULU_ACTIVATION_EPOCH);

    when(spec.getSpecConfig(any(UInt64.class))).thenReturn(specConfig);

    when(specConfig.toVersionFulu()).thenReturn(Optional.of(specConfigFulu));
    when(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests())
            .thenReturn(CUSTODY_PERIOD_EPOCHS);

    when(spec.computeEpochAtSlot(any(UInt64.class)))
            .thenAnswer(invocation -> {
              UInt64 slot = invocation.getArgument(0);
              return slot.dividedBy(SLOTS_PER_EPOCH);
            });

    when(spec.computeStartSlotAtEpoch(any(UInt64.class)))
            .thenAnswer(invocation -> {
              UInt64 epoch = invocation.getArgument(0);
              return epoch.times(SLOTS_PER_EPOCH);
            });
  }

  @Test
  void shouldClampResultToFuluActivationWhenCalculatedPeriodIsEarlier() {
    final UInt64 currentEpoch = FULU_ACTIVATION_EPOCH.plus(CUSTODY_PERIOD_EPOCHS-1);
    final UInt64 currentSlot = currentEpoch.times(SLOTS_PER_EPOCH);

    final MinCustodyPeriodSlotCalculator calculator =
            MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final UInt64 resultSlot = calculator.getMinCustodyPeriodSlot(currentSlot);

    final UInt64 expectedEpoch = FULU_ACTIVATION_EPOCH; // 100
    assertThat(resultSlot).isEqualTo(expectedEpoch.times(SLOTS_PER_EPOCH));
  }

  @Test
  void shouldReturnCalculatedPeriodWhenWellAfterFuluActivation() {
    final UInt64 currentEpoch = FULU_ACTIVATION_EPOCH.plus(CUSTODY_PERIOD_EPOCHS+10);
    final UInt64 currentSlot = currentEpoch.times(SLOTS_PER_EPOCH);

    final MinCustodyPeriodSlotCalculator calculator =
            MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final UInt64 resultSlot = calculator.getMinCustodyPeriodSlot(currentSlot);

    final UInt64 expectedEpoch = UInt64.valueOf(110);
    assertThat(resultSlot).isEqualTo(expectedEpoch.times(SLOTS_PER_EPOCH));
  }

  @Test
  void shouldHandleExactBoundaryAtFuluActivation() {
    final UInt64 currentEpoch = FULU_ACTIVATION_EPOCH;
    final UInt64 currentSlot = currentEpoch.times(SLOTS_PER_EPOCH);

    final MinCustodyPeriodSlotCalculator calculator =
            MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final UInt64 resultSlot = calculator.getMinCustodyPeriodSlot(currentSlot);
    
    assertThat(resultSlot).isEqualTo(FULU_ACTIVATION_EPOCH.times(SLOTS_PER_EPOCH));
  }
}
