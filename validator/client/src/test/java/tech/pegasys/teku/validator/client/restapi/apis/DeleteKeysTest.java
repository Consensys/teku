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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DeleteKeysTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());

  @Test
  void metadata_shouldProduceCorrectOpenApi() throws Exception {
    final DeleteKeys endpoint = new DeleteKeys();
    final String json = JsonTestUtil.serializeEndpointMetadata(endpoint);
    final Map<String, Object> result = JsonTestUtil.parse(json);

    final Map<String, Object> expected =
        JsonTestUtil.parseJsonResource(PostKeysTest.class, "DeleteKeys.json");

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldBeNotImplemented() throws Exception {
    final DeleteKeys endpoint = new DeleteKeys();
    final RestApiRequest request = mock(RestApiRequest.class);
    when(request.getRequestBody()).thenReturn(List.of(dataStructureUtil.randomPublicKey()));
    endpoint.handle(request);

    verify(request).respondError(SC_INTERNAL_SERVER_ERROR, "Not implemented");
  }
}
