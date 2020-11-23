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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.validator.AttesterDuty;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PostAttesterDutiesTest extends AbstractValidatorApiTest {

  @BeforeEach
  public void setup() {
    handler = new PostAttesterDuties(syncDataProvider, validatorDataProvider, jsonProvider);
  }

  @Test
  public void shouldReturnServiceUnavailableWhenResultIsEmpty() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(false);
    when(context.pathParamMap()).thenReturn(Map.of("epoch", "100"));
    when(context.body()).thenReturn("[\"2\"]");
    when(validatorDataProvider.getAttesterDuties(eq(UInt64.valueOf(100)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);
    verifyStatusCode(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldGetAttesterDuties() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(false);
    when(context.pathParamMap()).thenReturn(Map.of("epoch", "100"));
    when(context.body()).thenReturn("[\"2\"]");

    PostAttesterDutiesResponse duties =
        new PostAttesterDutiesResponse(
            Bytes32.fromHexString("0x1234"),
            List.of(getDuty(2, 1, 2, 10, 3, compute_start_slot_at_epoch(UInt64.valueOf(100)))));
    when(validatorDataProvider.getAttesterDuties(eq(UInt64.valueOf(100)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(duties)));

    handler.handle(context);
    PostAttesterDutiesResponse response = getResponseFromFuture(PostAttesterDutiesResponse.class);
    assertThat(response).isEqualTo(duties);
  }

  @Test
  public void shouldReturnBadRequestWhenIllegalArgumentExceptionThrown() throws Exception {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.isSyncActive()).thenReturn(false);
    when(context.pathParamMap()).thenReturn(Map.of("epoch", "100"));
    when(context.body()).thenReturn("[\"2\"]");
    when(validatorDataProvider.getAttesterDuties(UInt64.valueOf(100), List.of(2)))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("Bad epoch")));

    handler.handle(context);
    verifyStatusCode(SC_BAD_REQUEST);
    final BadRequest badRequest = getBadRequestFromFuture();
    assertThat(badRequest).usingRecursiveComparison().isEqualTo(new BadRequest(400, "Bad epoch"));
  }

  AttesterDuty getDuty(
      final long validatorIndex,
      final long committeeIndex,
      final long committeeLength,
      final int committeesAtSlot,
      final long validatorCommitteeIndex,
      final UInt64 slot) {
    return new AttesterDuty(
        new BLSPubKey(BLSPublicKey.random(1)),
        UInt64.valueOf(validatorIndex),
        UInt64.valueOf(committeeIndex),
        UInt64.valueOf(committeeLength),
        UInt64.valueOf(committeesAtSlot),
        UInt64.valueOf(validatorCommitteeIndex),
        slot);
  }
}
