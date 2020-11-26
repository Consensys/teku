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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateRootResponse;
import tech.pegasys.teku.api.schema.Root;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class GetStateRootTest extends AbstractBeaconHandlerTest {
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final Root root = new Root(dataStructureUtil.randomBytes32());

  @Test
  public void shouldReturnRootInfo() throws Exception {
    final GetStateRoot handler = new GetStateRoot(chainDataProvider, jsonProvider);
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(chainDataProvider.getStateRoot("head"))
        .thenReturn(SafeFuture.completedFuture(Optional.of(root)));

    handler.handle(context);

    final GetStateRootResponse response = getResponseFromFuture(GetStateRootResponse.class);
    assertThat(root).isEqualTo(response.data);
  }
}
