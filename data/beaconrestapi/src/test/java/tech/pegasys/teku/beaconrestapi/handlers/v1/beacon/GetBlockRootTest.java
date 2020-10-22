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

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockResponse;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetBlockRootTest extends AbstractBeaconHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GetBlockRoot handler = new GetBlockRoot(chainDataProvider, jsonProvider);
  private final SignedBeaconBlock signedBeaconBlock =
      new SignedBeaconBlock(dataStructureUtil.randomSignedBeaconBlock(1));

  @Test
  public void shouldGetBlockRootBySlot() throws Exception {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    when(context.pathParamMap()).thenReturn(Map.of("block_id", "head"));
    when(chainDataProvider.parameterToSlot("head")).thenReturn(Optional.of(slot));
    when(chainDataProvider.getBlockBySlotV1(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedBeaconBlock)));
    when(chainDataProvider.getBeaconHead())
        .thenReturn(Optional.of(new BeaconHead(slot, Bytes32.ZERO, Bytes32.ZERO)));
    handler.handle(context);
    GetBlockResponse response = getResponseFromFuture(GetBlockResponse.class);
    assertThat(response.data).isEqualTo(signedBeaconBlock);
  }
}
