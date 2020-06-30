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

package tech.pegasys.teku.beaconrestapi.beacon;

import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.node.GetVersion;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class RestApiHostAllowlistIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnForbiddenIfHostNotAuthorized() throws Exception {
    final TekuConfiguration config =
        TekuConfiguration.builder()
            .setRestApiPort(0)
            .setRestApiEnabled(true)
            .setRestApiDocsEnabled(false)
            .setRestApiHostAllowlist(List.of("not.authorized.host"))
            .build();
    startPreGenesisRestAPIWithConfig(config);

    final Response response = getLatest();
    assertForbidden(response);
  }

  private Response getLatest() throws IOException {
    return getResponse(GetVersion.ROUTE);
  }
}
