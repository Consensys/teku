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

package tech.pegasys.artemis.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.api.schema.BeaconValidators.PAGE_SIZE_DEFAULT;
import static tech.pegasys.artemis.api.schema.BeaconValidators.PAGE_TOKEN_DEFAULT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ACTIVE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_SIZE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.api.schema.BeaconValidators;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.MutableValidator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class GetValidatorsTest {
  private Context context = mock(Context.class);
  private final UnsignedLong epoch = DataStructureUtil.randomUnsignedLong(99);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final Bytes32 blockRoot = DataStructureUtil.randomBytes32(99);
  private final tech.pegasys.artemis.datastructures.state.BeaconState beaconStateInternal =
      DataStructureUtil.randomBeaconState(98);
  private final BeaconState beaconState = new BeaconState(beaconStateInternal);

  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @Test
  public void shouldReturnValidatorsWhenBlockRoot() throws Exception {
    GetValidators handler = new GetValidators(provider, jsonProvider);
    BeaconValidators beaconValidators = new BeaconValidators(beaconStateInternal);

    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(provider.getStateByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(provider).getBestBlockRoot();
    verify(provider).getStateByBlockRoot(blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertThat(beaconValidators.validators.size())
        .isEqualTo(Math.min(PAGE_SIZE_DEFAULT, beaconStateInternal.getValidators().size()));
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnEmptyListWhenNoValidators() throws Exception {
    GetValidators handler = new GetValidators(provider, jsonProvider);
    MutableBeaconState beaconStateW = this.beaconStateInternal.createWritableCopy();
    beaconStateW.getValidators().clear();

    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(provider.getStateByBlockRoot(blockRoot))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(new BeaconState(beaconStateW.commitChanges()))));

    handler.handle(context);

    verify(provider).getBestBlockRoot();
    verify(provider).getStateByBlockRoot(blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(new BeaconValidators()));
  }

  @Test
  public void shouldReturnValidatorsWhenQueryByEpoch() throws Exception {
    GetValidators handler = new GetValidators(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of(epoch.toString())));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    BeaconValidators beaconValidators = new BeaconValidators(beaconStateInternal);

    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getStateAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(provider).getStateAtSlot(slot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertThat(beaconValidators.validators.size())
        .isEqualTo(Math.min(PAGE_SIZE_DEFAULT, beaconStateInternal.getValidators().size()));
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnActiveValidatorsWhenQueryByActiveAndEpoch() throws Exception {
    GetValidators handler = new GetValidators(provider, jsonProvider);
    when(context.queryParamMap())
        .thenReturn(Map.of(ACTIVE, List.of("true"), EPOCH, List.of(epoch.toString())));
    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    final tech.pegasys.artemis.datastructures.state.BeaconState
        beaconStateWithAddedActiveValidator = addActiveValidator(beaconStateInternal);

    BeaconValidators beaconActiveValidators =
        new BeaconValidators(
            beaconStateWithAddedActiveValidator,
            true,
            epoch,
            PAGE_SIZE_DEFAULT,
            PAGE_TOKEN_DEFAULT);

    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getStateAtSlot(slot))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new BeaconState(beaconStateWithAddedActiveValidator))));

    handler.handle(context);

    verify(provider).getStateAtSlot(slot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconActiveValidators));
  }

  @Test
  public void shouldReturnActiveValidatorsWhenQueryByActiveOnly() throws Exception {
    GetValidators handler = new GetValidators(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(ACTIVE, List.of("true")));
    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    final tech.pegasys.artemis.datastructures.state.BeaconState beaconStateWithAddedValidator =
        addActiveValidator(beaconStateInternal);
    BeaconValidators beaconActiveValidators =
        new BeaconValidators(
            beaconStateWithAddedValidator,
            true,
            BeaconStateUtil.get_current_epoch(beaconStateInternal),
            PAGE_SIZE_DEFAULT,
            PAGE_TOKEN_DEFAULT);

    BeaconState result = new BeaconState(beaconStateWithAddedValidator);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getStateByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(result)));
    when(provider.getStateAtSlot(slot)).thenReturn(SafeFuture.completedFuture(Optional.of(result)));

    handler.handle(context);

    verify(provider).getBestBlockRoot();
    verify(provider).getStateByBlockRoot(blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconActiveValidators));
  }

  @Test
  public void shouldReturnSubsetOfValidatorsWhenQueryByEpochAndPageSize() throws Exception {
    GetValidators handler = new GetValidators(provider, jsonProvider);
    final int suppliedPageSizeParam = 10;
    when(context.queryParamMap())
        .thenReturn(
            Map.of(
                EPOCH,
                List.of(epoch.toString()),
                PAGE_SIZE,
                List.of(String.valueOf(suppliedPageSizeParam))));
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);

    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconStateInternal, false, epoch, suppliedPageSizeParam, PAGE_TOKEN_DEFAULT);

    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getStateAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(new BeaconState(beaconStateInternal))));

    handler.handle(context);

    verify(provider).getStateAtSlot(slot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnSubsetOfValidatorsWhenQueryByEpochAndPageSizeAndPageToken()
      throws Exception {
    GetValidators handler = new GetValidators(provider, jsonProvider);
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

    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconStateInternal, false, epoch, suppliedPageSizeParam, suppliedPageTokenParam);

    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getStateAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(new BeaconState(beaconStateInternal))));

    handler.handle(context);

    verify(provider).getStateAtSlot(slot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnBadRequestWhenBadEpochParameterSpecified() throws Exception {
    final GetValidators handler = new GetValidators(provider, jsonProvider);
    when(context.queryParamMap())
        .thenReturn(Map.of(ACTIVE, List.of("true"), EPOCH, List.of("not-an-int")));
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnEmptyListWhenQueryByActiveAndFarFutureEpoch() throws Exception {
    final GetValidators handler = new GetValidators(provider, jsonProvider);
    final UnsignedLong farFutureSlot =
        BeaconStateUtil.compute_start_slot_at_epoch(Constants.FAR_FUTURE_EPOCH);
    when(context.queryParamMap())
        .thenReturn(
            Map.of(
                ACTIVE,
                List.of("true"),
                EPOCH,
                List.of(String.valueOf(Constants.FAR_FUTURE_EPOCH))));
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(provider.getStateAtSlot(farFutureSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(new BeaconValidators()));
  }

  private tech.pegasys.artemis.datastructures.state.BeaconState addActiveValidator(
      final tech.pegasys.artemis.datastructures.state.BeaconState beaconState) {
    MutableBeaconState beaconStateW = beaconState.createWritableCopy();

    // create an ACTIVE validator and add it to the list
    MutableValidator v = DataStructureUtil.randomValidator(88).createWritableCopy();
    v.setActivation_eligibility_epoch(UnsignedLong.ZERO);
    v.setActivation_epoch(UnsignedLong.valueOf(Constants.GENESIS_EPOCH));
    assertThat(
            ValidatorsUtil.is_active_validator(v, BeaconStateUtil.get_current_epoch(beaconState)))
        .isTrue();
    beaconStateW.getValidators().add(v);
    // also add balance
    beaconStateW.getBalances().add(UnsignedLong.ZERO);
    return beaconStateW.commitChanges();
  }
}
