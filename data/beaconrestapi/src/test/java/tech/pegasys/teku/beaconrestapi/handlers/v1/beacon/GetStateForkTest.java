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
import tech.pegasys.teku.api.response.v1.beacon.GetStateForkResponse;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class GetStateForkTest extends AbstractBeaconHandlerTest {
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final Fork fork = new Fork(dataStructureUtil.randomFork());

  @Test
  public void shouldReturnForkInfo() throws Exception {
    final GetStateFork handler = new GetStateFork(chainDataProvider, jsonProvider);
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head"));
    when(chainDataProvider.getStateFork("head"))
        .thenReturn(SafeFuture.completedFuture(Optional.of(fork)));

    handler.handle(context);

    final GetStateForkResponse response = getResponseFromFuture(GetStateForkResponse.class);
    assertThat(fork).isEqualTo(response.data);
  }
}
