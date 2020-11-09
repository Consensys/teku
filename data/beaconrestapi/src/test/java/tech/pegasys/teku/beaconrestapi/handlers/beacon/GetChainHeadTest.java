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

package tech.pegasys.teku.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.BeaconChainHead;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.provider.JsonProvider;

public class GetChainHeadTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private Context context = mock(Context.class);
  private ChainDataProvider provider = mock(ChainDataProvider.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private BeaconBlockAndState blockAndState = dataStructureUtil.randomBlockAndState(1);

  @Test
  public void shouldReturnBeaconChainHeadResponse() throws Exception {
    final GetChainHead handler = new GetChainHead(provider, jsonProvider);
    final BeaconChainHead beaconChainHead = new BeaconChainHead(blockAndState);
    final String expected = jsonProvider.objectToJSON(beaconChainHead);

    when(provider.getHeadState()).thenReturn(Optional.of(beaconChainHead));

    handler.handle(context);

    verify(context).result(expected);
  }

  @Test
  public void shouldReturnNoContentWhenStateIsNull() throws Exception {
    final GetChainHead handler = new GetChainHead(provider, jsonProvider);

    when(provider.getHeadState()).thenReturn(Optional.empty());

    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }
}
