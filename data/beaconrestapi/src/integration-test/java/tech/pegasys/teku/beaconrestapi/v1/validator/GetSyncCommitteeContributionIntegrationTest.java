/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.api.response.v1.validator.GetSyncCommitteeContributionResponse;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetSyncCommitteeContribution;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;

public class GetSyncCommitteeContributionIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {
  final Bytes32 blockRoot = Bytes32.random();
  BLSSignature sig = BLSSignature.empty();

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
  }

  @Test
  void shouldReturnNotFoundIfNotCreated() throws IOException {
    final SafeFuture<Optional<SyncCommitteeContribution>> future =
        SafeFuture.completedFuture(Optional.empty());

    when(validatorApiChannel.createSyncCommitteeContribution(eq(ONE), eq(1), eq(blockRoot)))
        .thenReturn(future);
    final Response response = get(ONE, 1, blockRoot);
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Integer.MIN_VALUE,
        -1,
        NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT,
        Integer.MAX_VALUE
      })
  void shouldRejectOutOfRangeSubcommitteeIndex(final int subcommittee) throws IOException {
    Response response = get(ONE, subcommittee, blockRoot);
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains(subcommittee + " is outside of this range.");
  }

  @Test
  void shouldReturnResultIfCreatedSuccessfully() throws IOException {
    final SafeFuture<Optional<SyncCommitteeContribution>> future =
        SafeFuture.completedFuture(
            Optional.of(
                spec.getSyncCommitteeUtilRequired(ONE)
                    .createSyncCommitteeContribution(
                        ONE, blockRoot, ONE, List.of(1), sig.asInternalBLSSignature())));

    when(validatorApiChannel.createSyncCommitteeContribution(eq(ONE), eq(1), eq(blockRoot)))
        .thenReturn(future);
    final Response response = get(ONE, 1, blockRoot);
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetSyncCommitteeContributionResponse r =
        jsonProvider.jsonToObject(
            response.body().string(), GetSyncCommitteeContributionResponse.class);
    assertThat(r.data.slot).isEqualTo(ONE);
    assertThat(r.data.beaconBlockRoot).isEqualTo(blockRoot);
  }

  public Response get(final UInt64 slot, final Integer subcommitteeIndex, final Bytes32 blockRoot)
      throws IOException {
    return getResponse(
        GetSyncCommitteeContribution.ROUTE,
        Map.of(
            "slot", slot.toString(),
            "subcommittee_index", subcommitteeIndex.toString(),
            "beacon_block_root", blockRoot.toHexString()));
  }
}
