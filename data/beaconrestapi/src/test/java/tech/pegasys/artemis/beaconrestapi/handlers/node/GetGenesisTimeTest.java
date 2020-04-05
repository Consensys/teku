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

package tech.pegasys.artemis.beaconrestapi.handlers.node;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_FINALIZED;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.provider.JsonProvider;

public class GetGenesisTimeTest {
  private Context context = mock(Context.class);
  private final UnsignedLong genesisTime = UnsignedLong.valueOf(51234);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  @Test
  public void shouldReturnGenesisTimeWhenSet() throws Exception {
    GetGenesisTime handler = new GetGenesisTime(provider, jsonProvider);
    when(provider.getGenesisTime()).thenReturn(genesisTime);
    handler.handle(context);
    verify(context).result(jsonProvider.objectToJSON(genesisTime));
    verify(context).header(Header.CACHE_CONTROL, CACHE_FINALIZED);
  }
}
