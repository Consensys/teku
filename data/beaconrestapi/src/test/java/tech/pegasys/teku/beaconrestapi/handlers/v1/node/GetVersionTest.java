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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.node.Version;
import tech.pegasys.teku.api.response.v1.node.VersionResponse;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.util.cli.VersionProvider;

public class GetVersionTest extends AbstractBeaconHandlerTest {
  private final VersionResponse versionResponse =
      new VersionResponse(new Version(VersionProvider.VERSION));

  @Test
  public void shouldReturnVersionString() throws Exception {
    GetVersion handler = new GetVersion(jsonProvider);
    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);

    VersionResponse response = getResponseObject(VersionResponse.class);
    assertThat(response).isEqualTo(versionResponse);
  }
}
