/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.validator.coordinator;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.MutableValidator;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableRefList;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.api.ValidatorDuties;

class ValidatorApiHandlerTest {

  private static final UnsignedLong EPOCH = UnsignedLong.valueOf(13);
  private static final UnsignedLong START_SLOT = BeaconStateUtil.compute_start_slot_at_epoch(EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);

  private final ValidatorApiHandler validatorApiHandler =
      new ValidatorApiHandler(chainDataClient, blockFactory);

  @Test
  public void getDuties_shouldReturnEmptyWhenStateIsUnavailable() {
    when(chainDataClient.getStateAtSlot(START_SLOT)).thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<List<ValidatorDuties>>> duties =
        validatorApiHandler.getDuties(EPOCH, List.of(dataStructureUtil.randomPublicKey()));
    assertThat(duties).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getDuties_shouldReturnDutiesForUnknownValidator() {
    when(chainDataClient.getStateAtSlot(START_SLOT))
        .thenReturn(completedFuture(Optional.of(createStateWithActiveValidators())));

    final BLSPublicKey unknownPublicKey = dataStructureUtil.randomPublicKey();
    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(EPOCH, List.of(unknownPublicKey));
    final Optional<List<ValidatorDuties>> duties = assertCompletedSuccessfully(result);
    assertThat(duties.get()).containsExactly(ValidatorDuties.noDuties(unknownPublicKey));
  }

  @Test
  public void getDuties_shouldReturnDutiesForKnownValidator() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlot(START_SLOT))
        .thenReturn(completedFuture(Optional.of(state)));

    final int validatorIndex = 3;
    final BLSPublicKey publicKey = state.getValidators().get(validatorIndex).getPubkey();
    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(EPOCH, List.of(publicKey));
    final Optional<List<ValidatorDuties>> duties = assertCompletedSuccessfully(result);
    assertThat(duties.get())
        .containsExactly(
            ValidatorDuties.withDuties(
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
    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        validatorApiHandler.getDuties(
            EPOCH, List.of(validator3Key, unknownPublicKey, validator31Key));
    final Optional<List<ValidatorDuties>> duties = assertCompletedSuccessfully(result);
    final ValidatorDuties validator3Duties =
        ValidatorDuties.withDuties(validator3Key, 3, 0, emptyList(), UnsignedLong.valueOf(110));
    final ValidatorDuties unknownValidatorDuties = ValidatorDuties.noDuties(unknownPublicKey);
    final ValidatorDuties validator6Duties =
        ValidatorDuties.withDuties(
            validator31Key,
            31,
            0,
            List.of(UnsignedLong.valueOf(107), UnsignedLong.valueOf(111)),
            UnsignedLong.valueOf(104));
    assertThat(duties.get())
        .containsExactly(validator3Duties, unknownValidatorDuties, validator6Duties);
  }

  @Test
  public void createUnsignedBlock_shouldFailWithChainDataUnavailableWhenStoreIsNotSet() {
    when(chainDataClient.getStore()).thenReturn(null);

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(
            UnsignedLong.ONE, dataStructureUtil.randomSignature());

    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void createUnsignedBlock_shouldReturnEmptyWhenBestBlockNotSet() {
    final Store store = mock(Store.class);
    when(chainDataClient.getStore()).thenReturn(store);
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.empty());

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(
            UnsignedLong.ONE, dataStructureUtil.randomSignature());

    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void createUnsignedBlock_shouldCreateBlock() throws Exception {
    final Store store = mock(Store.class);
    final UnsignedLong newSlot = UnsignedLong.valueOf(25);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BeaconState previousState = dataStructureUtil.randomBeaconState();
    final BeaconBlock previousBlock =
        dataStructureUtil.randomBeaconBlock(previousState.getSlot().longValue());
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock createdBlock = dataStructureUtil.randomBeaconBlock(newSlot.longValue());

    when(chainDataClient.getStore()).thenReturn(store);
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(store.getBlockState(blockRoot)).thenReturn(previousState);
    when(store.getBlock(blockRoot)).thenReturn(previousBlock);
    when(blockFactory.createUnsignedBlock(previousState, previousBlock, newSlot, randaoReveal))
        .thenReturn(createdBlock);

    final SafeFuture<Optional<BeaconBlock>> result =
        validatorApiHandler.createUnsignedBlock(newSlot, randaoReveal);

    assertThat(result).isCompletedWithValue(Optional.of(createdBlock));
  }

  private Optional<List<ValidatorDuties>> assertCompletedSuccessfully(
      final SafeFuture<Optional<List<ValidatorDuties>>> result) {
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
