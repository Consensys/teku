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

import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequestImpl;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class GetVersionTest extends AbstractMigratedBeaconHandlerTest {

  @Test
  public void shouldReturnVersionString() throws Exception {
    final RestApiRequest request = mock(RestApiRequestImpl.class);
    final GetVersion handler = new GetVersion();

    handler.handleRequest(request);
    verify(request).respondOk(refEq(VersionProvider.VERSION));
  }
}
