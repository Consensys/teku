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

package tech.pegasys.teku.beaconrestapi.handlers.node;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.util.cli.VersionProvider;

public class GetVersionTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  public void shouldReturnVersionString() throws Exception {
    GetVersion handler = new GetVersion(jsonProvider);
    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(jsonProvider.objectToJSON(VersionProvider.VERSION));
  }
}
