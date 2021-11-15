/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostKeysTest {

  @Test
  void shouldBeNotImplemented() throws Exception {
    final PostKeys endpoint = new PostKeys();
    final RestApiRequest request = mock(RestApiRequest.class);
    endpoint.handle(request);

    verify(request).respondError(SC_INTERNAL_SERVER_ERROR, "Not implemented");
  }
}
