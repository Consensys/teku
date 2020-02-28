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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconValidatorsResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class BeaconValidatorsHandlerTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final CombinedChainDataClient combinedClient = mock(CombinedChainDataClient.class);
  private final Bytes32 blockRoot = DataStructureUtil.randomBytes32(99);
  private final BeaconState beaconState = DataStructureUtil.randomBeaconState(98);

  @Captor private ArgumentCaptor<SafeFuture<String>> args;

  @Test
  public void shouldReturnValidatorsWhenBlockRoot() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);
    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(beaconState.getValidators());

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(combinedClient.getStateByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(combinedClient).getBestBlockRoot();
    verify(combinedClient).getStateByBlockRoot(blockRoot);
    verify(context).result(args.capture());

    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconValidators));
  }

  @Test
  public void shouldReturnNoContentWhenNoBlockRoot() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(combinedClient, jsonProvider);

    when(combinedClient.getBestBlockRoot()).thenReturn(Optional.empty());
    handler.handle(context);
    verify(context).status(SC_NO_CONTENT);
  }
}
