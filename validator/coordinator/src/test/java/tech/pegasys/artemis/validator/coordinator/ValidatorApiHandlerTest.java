package tech.pegasys.artemis.validator.coordinator;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.MutableValidator;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableRefList;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.api.ValidatorDuties;

class ValidatorApiHandlerTest {

  private static final UnsignedLong EPOCH = UnsignedLong.valueOf(13);
  private static final UnsignedLong START_SLOT = BeaconStateUtil.compute_start_slot_at_epoch(EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);

  private final ValidatorApiHandler validatorApiHandler = new ValidatorApiHandler(chainDataClient);

  @Test
  public void getDuties_shouldReturnEmptyWhenStateIsUnavailable() {
    when(chainDataClient.getStateAtSlot(START_SLOT)).thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<List<ValidatorDuties>> duties =
        validatorApiHandler.getDuties(EPOCH, List.of(dataStructureUtil.randomPublicKey()));
    assertThat(duties).isCompletedWithValue(emptyList());
  }

  @Test
  public void getDuties_shouldReturnNoDutiesForUnknownValidator() {
    when(chainDataClient.getStateAtSlot(START_SLOT))
        .thenReturn(completedFuture(Optional.of(createStateWithActiveValidators())));

    final BLSPublicKey unknownPublicKey = dataStructureUtil.randomPublicKey();
    final SafeFuture<List<ValidatorDuties>> result =
        validatorApiHandler.getDuties(EPOCH, List.of(unknownPublicKey));
    final List<ValidatorDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties).containsExactly(ValidatorDuties.forUnknownValidator(unknownPublicKey));
  }

  @Test
  public void getDuties_shouldReturnDutiesForKnownValidator() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlot(START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));

    final int validatorIndex = 3;
    final BLSPublicKey publicKey = state.getValidators().get(validatorIndex).getPubkey();
    final SafeFuture<List<ValidatorDuties>> result =
        validatorApiHandler.getDuties(EPOCH, List.of(publicKey));
    final List<ValidatorDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties)
        .containsExactly(
            ValidatorDuties.forKnownValidator(
                publicKey, validatorIndex, 0, emptyList(), UnsignedLong.valueOf(110)));
  }

  @Test
  public void getDuties_shouldReturnDutiesForMixOfKnownAndUnknownValidators() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlot(START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));

    final BLSPublicKey unknownPublicKey = dataStructureUtil.randomPublicKey();
    final BLSPublicKey validator3Key = state.getValidators().get(3).getPubkey();
    final BLSPublicKey validator31Key = state.getValidators().get(31).getPubkey();
    final SafeFuture<List<ValidatorDuties>> result =
        validatorApiHandler.getDuties(
            EPOCH, List.of(validator3Key, unknownPublicKey, validator31Key));
    final List<ValidatorDuties> duties = assertCompletedSuccessfully(result);
    final ValidatorDuties validator3Duties =
        ValidatorDuties.forKnownValidator(
            validator3Key, 3, 0, emptyList(), UnsignedLong.valueOf(110));
    final ValidatorDuties unknownValidatorDuties =
        ValidatorDuties.forUnknownValidator(unknownPublicKey);
    final ValidatorDuties validator6Duties =
        ValidatorDuties.forKnownValidator(
            validator31Key,
            31,
            0,
            List.of(UnsignedLong.valueOf(107), UnsignedLong.valueOf(111)),
            UnsignedLong.valueOf(104));
    assertThat(duties).containsExactly(validator3Duties, unknownValidatorDuties, validator6Duties);
  }

  private List<ValidatorDuties> assertCompletedSuccessfully(
      final SafeFuture<List<ValidatorDuties>> result) {
    assertThat(result).isCompleted();
    return result.join();
  }

  private BeaconState createStateWithActiveValidators() {
    final MutableBeaconState state = dataStructureUtil.randomBeaconState(32).createWritableCopy();
    state.setSlot(START_SLOT);
    final SSZMutableRefList<Validator, MutableValidator> validators = state.getValidators();
    for (int i = 0; i < validators.size(); i++) {
      final MutableValidator validator = validators.get(i);
      validator.setActivation_eligibility_epoch(UnsignedLong.ZERO);
      validator.setActivation_epoch(UnsignedLong.ZERO);
      validator.setExit_epoch(Constants.FAR_FUTURE_EPOCH);
      validator.setWithdrawable_epoch(Constants.FAR_FUTURE_EPOCH);
    }
    return state.commitChanges();
  }
}
