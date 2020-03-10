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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconChainHead;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class BeaconChainHeadHandlerTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private Context context = mock(Context.class);
  private ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);

  @Captor private ArgumentCaptor<SafeFuture<String>> args;

  @Test
  public void shouldReturnBeaconChainHeadResponse() throws Exception {
    final BeaconChainHeadHandler handler =
        new BeaconChainHeadHandler(chainDataProvider, jsonProvider);
    final BeaconStateImpl beaconState = new BeaconStateImpl();
    final BeaconChainHead beaconChainHead = new BeaconChainHead(beaconState);

    when(chainDataProvider.getHeadState())
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(context).result(args.capture());
    assertThat(args.getValue().get()).isEqualTo(jsonProvider.objectToJSON(beaconChainHead));
  }

  @Test
  public void shouldReturnNoContentWhenStateIsNull() throws Exception {
    final BeaconChainHeadHandler handler =
        new BeaconChainHeadHandler(chainDataProvider, jsonProvider);

    when(chainDataProvider.getHeadState()).thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }
}
