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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.v1.beacon.EpochCommitteeResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateCommitteesResponse;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateCommitteesTest extends AbstractBeaconHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GetStateCommittees handler =
      new GetStateCommittees(chainDataProvider, jsonProvider);
  private final EpochCommitteeResponse epochCommitteeResponse =
      new EpochCommitteeResponse(
          ONE, ONE, List.of(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));

  @Test
  public void shouldGetCommitteesFromState() throws Exception {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final UInt64 epoch = compute_epoch_at_slot(dataStructureUtil.randomUInt64());
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(context.queryParamMap())
        .thenReturn(
            Map.of(
                "index", List.of("1"),
                "slot", List.of(slot.toString()),
                "epoch", List.of(epoch.toString())));
    when(chainDataProvider.getStateCommittees(
            "head", Optional.of(epoch), Optional.of(UInt64.ONE), Optional.of(slot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(List.of(epochCommitteeResponse))));
    handler.handle(context);
    GetStateCommitteesResponse response = getResponseFromFuture(GetStateCommitteesResponse.class);
    assertThat(response.data).isEqualTo(List.of(epochCommitteeResponse));
  }

  @ParameterizedTest
  @MethodSource("getParameters")
  public void shouldFailIfEpochInvalid(
      final String queryParameter, final String queryParameterValue) throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(context.queryParamMap()).thenReturn(Map.of(queryParameter, List.of(queryParameterValue)));
    assertThrows(BadRequestException.class, () -> handler.handle(context));
  }

  static Stream<Arguments> getParameters() {
    Stream.Builder<Arguments> builder = Stream.builder();
    builder
        .add(Arguments.of("epoch", "a"))
        .add(Arguments.of("slot", "b"))
        .add(Arguments.of("index", "c"));
    return builder.build();
  }
}
