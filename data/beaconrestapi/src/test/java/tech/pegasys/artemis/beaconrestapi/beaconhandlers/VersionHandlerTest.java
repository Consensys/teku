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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.cli.VersionProvider;

public class VersionHandlerTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  public void shouldReturnVersionString() throws Exception {
    VersionHandler handler = new VersionHandler(jsonProvider);
    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(jsonProvider.objectToJSON(VersionProvider.VERSION));
  }
}
