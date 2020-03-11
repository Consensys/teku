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

package tech.pegasys.artemis.beaconrestapi.validatorhandlers;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconValidatorsHandler;
import tech.pegasys.artemis.provider.JsonProvider;

@ExtendWith(MockitoExtension.class)
public class ValidatorDutiesHandlerTest {
  private Context context = mock(Context.class);
  //  private final UnsignedLong epoch = DataStructureUtil.randomUnsignedLong(99);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainDataProvider provider = mock(ChainDataProvider.class);
  //
  //  @Captor
  //  private ArgumentCaptor<SafeFuture<String>> args;

  @Test
  public void shouldReturnNoContentWhenNoBlockRoot() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(provider, jsonProvider);
    when(provider.getBestBlockRoot()).thenReturn(Optional.empty());
    handler.handle(context);
    verify(context).status(SC_NO_CONTENT);
  }
}
