package tech.pegasys.teku.spec.util;

import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.StubSpecProvider;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BouncyCastleExtension.class)
public class BeaconStateUtilTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final SpecProvider specProvider = StubSpecProvider.create();
  private final BeaconStateUtil beaconStateUtil = specProvider.atSlot(UInt64.ZERO).getBeaconStateUtil();
  private final SpecConstants specConstants = specProvider.atSlot(UInt64.ZERO).getConstants();
  private final long GENESIS_SLOT = specConstants.getGenesisSlot();
  private final long SLOTS_PER_EPOCH = specConstants.getSlotsPerEpoch();
  @BeforeEach
  void setup () {
  }

  @Test
  void succeedsWhenGetNextEpochReturnsTheEpochPlusOne() {
    BeaconState beaconState =
        createBeaconState().updated(state -> state.setSlot(UInt64.valueOf(specConstants.getGenesisSlot())));
    assertEquals(
        UInt64.valueOf(specConstants.getGenesisEpoch() + 1), beaconStateUtil.getNextEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot1() {
    BeaconState beaconState =
        createBeaconState().updated(state -> state.setSlot(UInt64.valueOf(specConstants.getGenesisSlot())));
    assertEquals(
        UInt64.valueOf(specConstants.getGenesisEpoch()), beaconStateUtil.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot2() {
    BeaconState beaconState =
        createBeaconState()
            .updated(
                state -> state.setSlot(UInt64.valueOf(specConstants.getGenesisSlot() + specConstants.getSlotsPerEpoch())));
    assertEquals(
        UInt64.valueOf(specConstants.getGenesisEpoch()), beaconStateUtil.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlotPlusOne() {
    BeaconState beaconState =
        createBeaconState()
            .updated(
                state ->
                    state.setSlot(UInt64.valueOf(specConstants.getGenesisSlot() + 2 * specConstants.getSlotsPerEpoch())));
    assertEquals(
        UInt64.valueOf(specConstants.getGenesisEpoch() + 1),
        beaconStateUtil.getPreviousEpoch(beaconState));
  }

  @Test
  void getPreviousDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(BeaconBlock.fromGenesisState(state).getRoot());
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + 3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochOne() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + SLOTS_PER_EPOCH + 3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfTwoEpochsAgo() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + SLOTS_PER_EPOCH * 2 + 3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get((int) (GENESIS_SLOT + SLOTS_PER_EPOCH - 1)));
  }

  @Test
  void compute_next_epoch_boundary_slotAtBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = beaconStateUtil.computeStartSlotAtEpoch(expectedEpoch);

    assertThat(beaconStateUtil.computeNextEpochBoundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void compute_next_epoch_boundary_slotPriorToBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = beaconStateUtil.computeStartSlotAtEpoch(expectedEpoch).minus(1);

    assertThat(beaconStateUtil.computeNextEpochBoundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void getCurrentDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT));
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(BeaconBlock.fromGenesisState(state).getRoot());
  }

  @Test
  void getCurrentDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + 3));
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfPriorEpoch() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + SLOTS_PER_EPOCH + 3));
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get((int) (GENESIS_SLOT + SLOTS_PER_EPOCH - 1)));
  }
  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(
      boolean addToList, UInt64 amount, Validator knownValidator) {
    return BeaconState.createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUInt64());
              beaconState.setFork(
                  new Fork(
                      specConstants.getGenesisForkVersion(),
                      specConstants.getGenesisForkVersion(),
                      UInt64.valueOf(specConstants.getGenesisEpoch())));

              List<Validator> validatorList =
                  new ArrayList<>(
                      Arrays.asList(
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator()));
              List<UInt64> balanceList =
                  new ArrayList<>(Collections.nCopies(3, specConstants.getMaxEffectiveBalance()));

              if (addToList) {
                validatorList.add(knownValidator);
                balanceList.add(amount);
              }

              beaconState
                  .getValidators()
                  .addAll(
                      SSZList.createMutable(
                          validatorList, specConstants.getValidatorRegistryLimit(), Validator.class));
              beaconState
                  .getBalances()
                  .addAll(
                      SSZList.createMutable(
                          balanceList, specConstants.getValidatorRegistryLimit(), UInt64.class));
            });
  }
}
