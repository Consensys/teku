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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.EpochCommitteeResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateEpochCommitteesResponse;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateEpochCommitteesTest extends AbstractBeaconHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GetStateEpochCommittees handler =
      new GetStateEpochCommittees(chainDataProvider, jsonProvider);
  private final EpochCommitteeResponse epochCommitteeResponse =
      new EpochCommitteeResponse(
          ONE, ONE, List.of(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));

  @Test
  public void shouldGetCommitteesFromState() throws Exception {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final UInt64 epoch = compute_epoch_at_slot(dataStructureUtil.randomUInt64());
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head", "epoch", epoch.toString()));
    when(context.queryParam("index")).thenReturn("1");
    when(context.queryParam("slot")).thenReturn(slot.toString());
    when(chainDataProvider.stateParameterToSlot("head")).thenReturn(Optional.of(slot));
    when(chainDataProvider.getCommitteesAtEpochBySlotV1(
            slot, epoch, Optional.of(slot), Optional.of(1)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(List.of(epochCommitteeResponse))));
    when(chainDataProvider.getBeaconHead())
        .thenReturn(Optional.of(new BeaconHead(slot, Bytes32.ZERO, Bytes32.ZERO)));
    handler.handle(context);
    GetStateEpochCommitteesResponse response =
        getResponseFromFuture(GetStateEpochCommitteesResponse.class);
    assertThat(response.data).isEqualTo(List.of(epochCommitteeResponse));
  }
}
