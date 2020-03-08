/*
 * Copyright 2019 ConsenSys AG.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_FINALIZED;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.provider.JsonProvider;

public class GenesisTimeHandlerTest {
  private Context context = mock(Context.class);
  private final UnsignedLong genesisTime = UnsignedLong.valueOf(51234);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  @Test
  public void shouldReturnNoContentWhenGenesisTimeIsNotSet() throws Exception {
    GenesisTimeHandler handler = new GenesisTimeHandler(provider, jsonProvider);
    when(provider.getGenesisTime()).thenReturn(Optional.empty());
    handler.handle(context);
    verify(context).status(SC_NO_CONTENT);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }

  @Test
  public void shouldReturnGenesisTimeWhenSet() throws Exception {
    GenesisTimeHandler handler = new GenesisTimeHandler(provider, jsonProvider);
    when(provider.getGenesisTime()).thenReturn(Optional.of(genesisTime));
    handler.handle(context);
    verify(context).result(jsonProvider.objectToJSON(genesisTime));
    verify(context).header(Header.CACHE_CONTROL, CACHE_FINALIZED);
  }
}
