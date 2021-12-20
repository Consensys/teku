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

package tech.pegasys.teku.beaconrestapi.schema;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.provider.JsonProvider;

public class BadRequestTest {
  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  void shouldSerializeFromCodeAndMessage() {
    final String string = BadRequest.serialize(jsonProvider, 1, "1");
    assertThat(string).isEqualTo("{\"code\":1,\"message\":\"1\"}");
  }

  @Test
  void shouldSerializeInternalError() throws JsonProcessingException {
    final String string = BadRequest.internalError(jsonProvider, "1");
    assertThat(string).isEqualTo(getExpectedString(SC_INTERNAL_SERVER_ERROR, "1"));
  }

  @Test
  void shouldSerializeBadRequest() throws JsonProcessingException {
    final String string = BadRequest.badRequest(jsonProvider, "1");
    assertThat(string).isEqualTo(getExpectedString(SC_BAD_REQUEST, "1"));
  }

  @Test
  void shouldSerializeServiceUnavailable() throws JsonProcessingException {
    final String string = BadRequest.serviceUnavailable(jsonProvider);
    assertThat(string)
        .isEqualTo(getExpectedString(SC_SERVICE_UNAVAILABLE, RestApiConstants.SERVICE_UNAVAILABLE));
  }

  private String getExpectedString(final Integer status, final String message)
      throws JsonProcessingException {
    BadRequest badRequest = new BadRequest(status, message);
    return jsonProvider.objectToJSON(badRequest);
  }
}
