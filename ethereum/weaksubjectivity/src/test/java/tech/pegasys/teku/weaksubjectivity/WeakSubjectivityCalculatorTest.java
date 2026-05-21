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

package tech.pegasys.teku.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class WeakSubjectivityCalculatorTest {
  private static final Spec SPEC = TestSpecFactory.createMainnetPhase0();

  @ParameterizedTest(name = "safetyDecay: {0}, avgBalance: {1}, validatorCount: {2}")
  @MethodSource("computeWeakSubjectivityParams")
  public void computeWeakSubjectivity(
      final UInt64 safetyDecay,
      final UInt64 avgActiveValidatorBalance,
      final int validatorCount,
      final int expectedResult) {
    final UInt64 totalActiveValidatorBalance = avgActiveValidatorBalance.times(validatorCount);
    final WeakSubjectivityConfig config =
        WeakSubjectivityConfig.builder().specProvider(SPEC).safetyDecay(safetyDecay).build();
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    UInt64 result =
        calculator.computeWeakSubjectivityPeriod(
            SPEC.getGenesisSpec(), validatorCount, totalActiveValidatorBalance);
    assertThat(result).isEqualTo(UInt64.valueOf(expectedResult));
  }

  @ParameterizedTest(name = "safetyDecay: {0}, avgBalance: {1}, validatorCount: {2}")
  @MethodSource("computeWeakSubjectivityParams")
  public void isWithinWeakSubjectivityPeriod(
      final UInt64 safetyDecay,
      final UInt64 avgActiveValidatorBalance,
      final int validatorCount,
      final int expectedWeakSubjectivityPeriod) {
    final UInt64 totalActiveValidatorBalance = avgActiveValidatorBalance.times(validatorCount);
    final WeakSubjectivityCalculator calculator =
        new WeakSubjectivityCalculator(
            SPEC,
            safetyDecay,
            WeakSubjectivityCalculator.StateCalculator.createStaticCalculator(
                validatorCount, totalActiveValidatorBalance));

    final UInt64 finalizedEpoch = UInt64.valueOf(10_000);
    final CheckpointState finalizedCheckpoint = createMockCheckpointState(finalizedEpoch);
    final UInt64 minSafeEpoch = finalizedEpoch.plus(expectedWeakSubjectivityPeriod);

    // Check the minimum safe epoch
    final UInt64 minEpochStartSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minEpochStartSlot))
        .isTrue();
    final UInt64 minEpochLastSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch.plus(1)).minus(1);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minEpochLastSlot))
        .isTrue();

    // Check first unsafe epoch
    final UInt64 minUnsafeSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch.plus(1));
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot))
        .isFalse();
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot.plus(1)))
        .isFalse();

    // Check subsequent epochs
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot.plus(100)))
        .isFalse();
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, minUnsafeSlot.plus(999)))
        .isFalse();

    // Check earlier safe epochs
    final UInt64 earlierEpochSlot = SPEC.computeStartSlotAtEpoch(minSafeEpoch).minus(1);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, earlierEpochSlot))
        .isTrue();
    assertThat(
            calculator.isWithinWeakSubjectivityPeriod(
                finalizedCheckpoint, earlierEpochSlot.minus(100)))
        .isTrue();

    // Check finalized epoch
    final UInt64 finalizedEpochSlot = SPEC.computeStartSlotAtEpoch(finalizedEpoch);
    assertThat(calculator.isWithinWeakSubjectivityPeriod(finalizedCheckpoint, finalizedEpochSlot))
        .isTrue();
  }

  @Test
  public void computeWeakSubjectivityPeriod_shouldUseElectraFormulaFromCheckpointState() {
    final Spec spec =
        TestSpecFactory.createMinimalElectra(
            builder -> builder.electraBuilder(e -> e.minPerEpochChurnLimitElectra(ETH_TO_GWEI)));
    final UInt64 safetyDecay = UInt64.valueOf(10);
    final BeaconState state =
        createStateWithSingleActiveConsolidatingValidator(spec, ETH_TO_GWEI.times(2048));
    final WeakSubjectivityCalculator calculator = createCalculator(spec, safetyDecay);
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final BeaconStateAccessorsElectra accessors =
        BeaconStateAccessorsElectra.required(specVersion.beaconStateAccessors());
    final UInt64 totalActiveBalance = accessors.getTotalActiveBalance(state);

    final UInt64 expected =
        computeElectraWeakSubjectivityPeriod(
            specVersion.getConfig(),
            totalActiveBalance,
            accessors.getBalanceChurnLimit(BeaconStateElectra.required(state)),
            safetyDecay);

    assertThat(calculator.computeWeakSubjectivityPeriod(createMockCheckpointState(spec, state)))
        .isEqualTo(expected);
    assertThat(calculator.computeWeakSubjectivityPeriod(specVersion, 1, totalActiveBalance))
        .isNotEqualTo(expected);
  }

  @Test
  public void computeWeakSubjectivityPeriod_shouldUseGloasChurnMixFromCheckpointState() {
    final Spec spec =
        TestSpecFactory.createMinimalGloas(
            builder ->
                builder
                    .electraBuilder(e -> e.minPerEpochChurnLimitElectra(ETH_TO_GWEI))
                    .gloasBuilder(
                        g ->
                            g.churnLimitQuotientGloas(65_536)
                                .consolidationChurnLimitQuotient(65_536)
                                .maxPerEpochActivationChurnLimitGloas(ETH_TO_GWEI.times(4096))));
    final UInt64 safetyDecay = UInt64.valueOf(10);
    final BeaconState state =
        createStateWithSingleActiveConsolidatingValidator(spec, ETH_TO_GWEI.times(2048));
    final WeakSubjectivityCalculator calculator = createCalculator(spec, safetyDecay);
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final BeaconStateAccessorsGloas accessors =
        BeaconStateAccessorsGloas.required(specVersion.beaconStateAccessors());
    final BeaconStateElectra stateElectra = BeaconStateElectra.required(state);
    final UInt64 delta =
        accessors
            .getExitChurnLimit(stateElectra)
            .times(2)
            .dividedBy(3)
            .plus(accessors.getActivationChurnLimit(stateElectra).dividedBy(3))
            .plus(accessors.getConsolidationChurnLimit(stateElectra));
    final UInt64 totalActiveBalance = accessors.getTotalActiveBalance(state);
    final UInt64 expected =
        computeElectraWeakSubjectivityPeriod(
            specVersion.getConfig(), totalActiveBalance, delta, safetyDecay);

    assertThat(calculator.computeWeakSubjectivityPeriod(createMockCheckpointState(spec, state)))
        .isEqualTo(expected);
    assertThat(
            computeElectraWeakSubjectivityPeriod(
                specVersion.getConfig(),
                totalActiveBalance,
                accessors.getBalanceChurnLimit(stateElectra),
                safetyDecay))
        .isNotEqualTo(expected);
  }

  // Parameters from the table here:
  // https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/weak-subjectivity.md#compute_weak_subjectivity_period
  public static Stream<Arguments> computeWeakSubjectivityParams() {
    return Stream.of(
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 32768, 504),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 65536, 752),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 131072, 1248),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 262144, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 524288, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(28), 1048576, 2241),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 32768, 665),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 65536, 1075),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 131072, 1894),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 262144, 3532),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 524288, 3532),
        Arguments.of(UInt64.valueOf(10), ETH_TO_GWEI.times(32), 1048576, 3532));
  }

  private WeakSubjectivityCalculator createCalculator(final Spec spec, final UInt64 safetyDecay) {
    final WeakSubjectivityConfig config =
        WeakSubjectivityConfig.builder().specProvider(spec).safetyDecay(safetyDecay).build();
    return WeakSubjectivityCalculator.create(config);
  }

  private BeaconState createStateWithSingleActiveConsolidatingValidator(
      final Spec spec, final UInt64 balance) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    return new BeaconStateTestBuilder(dataStructureUtil)
        .slot(0)
        .activeConsolidatingValidator(balance)
        .build();
  }

  private UInt64 computeElectraWeakSubjectivityPeriod(
      final SpecConfig specConfig,
      final UInt64 totalActiveBalance,
      final UInt64 delta,
      final UInt64 safetyDecay) {
    return safetyDecay
        .times(totalActiveBalance)
        .dividedBy(delta.times(2).times(100))
        .plus(specConfig.getMinValidatorWithdrawabilityDelay());
  }

  private CheckpointState createMockCheckpointState(final Spec spec, final BeaconState state) {
    final UInt64 finalizedEpoch = spec.computeEpochAtSlot(state.getSlot());
    final CheckpointState checkpointState = mock(CheckpointState.class);
    final Checkpoint checkpoint = mock(Checkpoint.class);

    when(checkpointState.getState()).thenReturn(state);
    when(checkpointState.getCheckpoint()).thenReturn(checkpoint);
    when(checkpointState.getEpoch()).thenReturn(finalizedEpoch);

    when(checkpoint.getEpoch()).thenReturn(finalizedEpoch);

    return checkpointState;
  }

  private CheckpointState createMockCheckpointState(final UInt64 finalizedEpoch) {
    final CheckpointState checkpointState = mock(CheckpointState.class);
    final Checkpoint checkpoint = mock(Checkpoint.class);
    final BeaconState state = mock(BeaconState.class);

    when(checkpointState.getState()).thenReturn(state);
    when(checkpointState.getCheckpoint()).thenReturn(checkpoint);
    when(checkpointState.getEpoch()).thenReturn(finalizedEpoch);

    when(state.getSlot()).thenReturn(SPEC.computeStartSlotAtEpoch(finalizedEpoch));
    when(checkpoint.getEpoch()).thenReturn(finalizedEpoch);

    return checkpointState;
  }
}
