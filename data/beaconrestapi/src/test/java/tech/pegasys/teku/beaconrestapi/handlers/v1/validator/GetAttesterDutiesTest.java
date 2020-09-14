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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.validator.AttesterDuty;
import tech.pegasys.teku.api.response.v1.validator.GetAttesterDutiesResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetAttesterDutiesTest extends AbstractBeaconHandlerTest {
  final GetAttesterDuties handler =
      new GetAttesterDuties(
          chainDataProvider, syncDataProvider, validatorDataProvider, jsonProvider);

  @Test
  public void shouldReturnNotReadyWhenSyncing() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(true);

    handler.handle(context);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnNotReadyWhenStoreNotReady() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handle(context);
    verify(syncService, never()).isSyncActive();
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnBadRequestIfEpochTooFarAhead() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(false);
    when(chainDataProvider.getCurrentEpoch()).thenReturn(UInt64.valueOf(98));
    when(context.pathParamMap()).thenReturn(Map.of("epoch", "100"));

    handler.handle(context);
    verifyStatusCode(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnEmptyListIfValidatorIndexOutOfBounds() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(false);
    when(chainDataProvider.getCurrentEpoch()).thenReturn(UInt64.valueOf(99));
    when(context.pathParamMap()).thenReturn(Map.of("epoch", "100"));
    when(context.queryParamMap()).thenReturn(Map.of("index", List.of("2")));
    when(validatorDataProvider.getAttesterDuties(eq(UInt64.valueOf(100)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);
    GetAttesterDutiesResponse response = getResponseFromFuture(GetAttesterDutiesResponse.class);
    assertThat(response.data).isEmpty();
  }

  @Test
  public void shouldGetAttesterDuties() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(false);
    when(chainDataProvider.getCurrentEpoch()).thenReturn(UInt64.valueOf(99));
    when(context.pathParamMap()).thenReturn(Map.of("epoch", "100"));
    when(context.queryParamMap()).thenReturn(Map.of("index", List.of("2")));
    List<AttesterDuty> duties =
        List.of(getDuty(2, 1, 2, 3, compute_start_slot_at_epoch(UInt64.valueOf(100))));
    when(validatorDataProvider.getAttesterDuties(eq(UInt64.valueOf(100)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(duties)));

    handler.handle(context);
    GetAttesterDutiesResponse response = getResponseFromFuture(GetAttesterDutiesResponse.class);
    assertThat(response.data).isEqualTo(duties);
  }

  AttesterDuty getDuty(
      final long validatorIndex,
      final long committeeIndex,
      final long committeeLength,
      final long validatorCommitteeIndex,
      final UInt64 slot) {
    return new AttesterDuty(
        new BLSPubKey(BLSPublicKey.random(1)),
        UInt64.valueOf(validatorIndex),
        UInt64.valueOf(committeeIndex),
        UInt64.valueOf(committeeLength),
        UInt64.valueOf(validatorCommitteeIndex),
        slot);
  }
}
