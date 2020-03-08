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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ACTIVE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_SIZE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_SIZE_DEFAULT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN_DEFAULT;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconValidatorsResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.MutableValidator;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

@ExtendWith(MockitoExtension.class)
public class BeaconValidatorsHandlerTest {
  private Context context = mock(Context.class);
  private final UnsignedLong epoch = DataStructureUtil.randomUnsignedLong(99);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final CombinedChainDataClient combinedClient = mock(CombinedChainDataClient.class);
  private final Bytes32 blockRoot = DataStructureUtil.randomBytes32(99);
  private final BeaconState beaconState = DataStructureUtil.randomBeaconState(98);

  @Captor private ArgumentCaptor<SafeFuture<String>> args;

  @Test
  public void shouldReturnValidatorsWhenBlockRoot() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(beaconState.getValidators());

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(combinedClient.getStateByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateByBlockRoot(blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertThat(beaconValidators.validatorList.size())
        .isEqualTo(Math.min(PAGE_SIZE_DEFAULT, beaconState.getValidators().size()));
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnEmptyListWhenNoValidators() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    MutableBeaconState beaconStateW = this.beaconState.createWritableCopy();
    beaconStateW.getValidators().clear();

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(combinedClient.getStateByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateW.commitChanges())));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateByBlockRoot(blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(
        data.get(),
        jsonProvider.objectToJSON(
            new BeaconValidatorsResponse(SSZList.createMutable(Validator.class, 0L))));
  }

  @Test
  public void shouldReturnNoContentWhenNoBlockRoot() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.empty());
    handler.handle(context);
    verify(context).status(SC_NO_CONTENT);
  }

  @Test
  public void shouldReturnValidatorsWhenQueryByEpoch() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of(epoch.toString())));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(beaconState.getValidators());

    when(combinedClient.getStateAtSlot(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateAtSlot(slot, blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertThat(beaconValidators.validatorList.size())
        .isEqualTo(Math.min(PAGE_SIZE_DEFAULT, beaconState.getValidators().size()));
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnActiveValidatorsWhenQueryByActiveAndEpoch() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    when(context.queryParamMap())
        .thenReturn(Map.of(ACTIVE, List.of("true"), EPOCH, List.of(epoch.toString())));
    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    final BeaconState beaconStateWithAddedActiveValidator = addActiveValidator(beaconState);

    BeaconValidatorsResponse beaconActiveValidators =
        new BeaconValidatorsResponse(
            beaconStateWithAddedActiveValidator.getValidators(),
            true,
            epoch,
            PAGE_SIZE_DEFAULT,
            PAGE_TOKEN_DEFAULT);

    when(combinedClient.getStateAtSlot(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateWithAddedActiveValidator)));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateAtSlot(slot, blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconActiveValidators));
  }

  @Test
  public void shouldReturnActiveValidatorsWhenQueryByActiveOnly() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(ACTIVE, List.of("true")));
    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    final BeaconState beaconStateWithAddedValidator = addActiveValidator(beaconState);
    BeaconValidatorsResponse beaconActiveValidators =
        new BeaconValidatorsResponse(
            beaconStateWithAddedValidator.getValidators(),
            true,
            BeaconStateUtil.get_current_epoch(beaconState),
            PAGE_SIZE_DEFAULT,
            PAGE_TOKEN_DEFAULT);

    when(combinedClient.getStateByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateWithAddedValidator)));
    when(combinedClient.getStateAtSlot(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateWithAddedValidator)));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateByBlockRoot(blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconActiveValidators));
  }

  @Test
  public void shouldReturnSubsetOfValidatorsWhenQueryByEpochAndPageSize() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    final int suppliedPageSizeParam = 10;
    when(context.queryParamMap())
        .thenReturn(
            Map.of(
                EPOCH,
                List.of(epoch.toString()),
                PAGE_SIZE,
                List.of(String.valueOf(suppliedPageSizeParam))));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(
            beaconState.getValidators(), false, epoch, suppliedPageSizeParam, PAGE_TOKEN_DEFAULT);

    when(combinedClient.getStateAtSlot(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateAtSlot(slot, blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnSubsetOfValidatorsWhenQueryByEpochAndPageSizeAndPageToken()
      throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    final int suppliedPageSizeParam = 10;
    final int suppliedPageTokenParam = 1;
    when(context.queryParamMap())
        .thenReturn(
            Map.of(
                EPOCH,
                List.of(epoch.toString()),
                PAGE_SIZE,
                List.of(String.valueOf(suppliedPageSizeParam)),
                PAGE_TOKEN,
                List.of(String.valueOf(suppliedPageTokenParam))));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(
            beaconState.getValidators(),
            false,
            epoch,
            suppliedPageSizeParam,
            suppliedPageTokenParam);

    when(combinedClient.getStateAtSlot(slot, blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateAtSlot(slot, blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnBadRequestWhenBadEpochParameterSpecified() throws Exception {
    final BeaconValidatorsHandler handler =
        new BeaconValidatorsHandler(combinedClient, jsonProvider);
    when(context.queryParamMap())
        .thenReturn(Map.of(ACTIVE, List.of("true"), EPOCH, List.of("not-an-int")));
    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnEmptyListWhenFutureEpochSpecified() throws Exception {
    final BeaconValidatorsHandler handler =
        new BeaconValidatorsHandler(combinedClient, jsonProvider);
    when(context.queryParamMap())
        .thenReturn(
            Map.of(
                ACTIVE,
                List.of("true"),
                EPOCH,
                List.of(String.valueOf(Constants.FAR_FUTURE_EPOCH))));
    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(combinedClient.getStateAtSlot(
            BeaconStateUtil.compute_start_slot_at_epoch(Constants.FAR_FUTURE_EPOCH), blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(
        data.get(),
        jsonProvider.objectToJSON(
            new BeaconValidatorsResponse(SSZList.createMutable(Validator.class, 0L))));
  }

  private BeaconState addActiveValidator(final BeaconState beaconState) {
    MutableBeaconState beaconStateW = beaconState.createWritableCopy();

    // create an ACTIVE validator and add it to the list
    MutableValidator v = DataStructureUtil.randomValidator(88).createWritableCopy();
    v.setActivation_eligibility_epoch(UnsignedLong.ZERO);
    v.setActivation_epoch(UnsignedLong.valueOf(Constants.GENESIS_EPOCH));

    assertThat(
            ValidatorsUtil.is_active_validator(v, BeaconStateUtil.get_current_epoch(beaconState)))
        .isTrue();

    v.setActivation_epoch(beaconState.getFinalized_checkpoint().getEpoch());
    beaconStateW.getValidators().add(v);
    return beaconStateW.commitChanges();
  }
}
