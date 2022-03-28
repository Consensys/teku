/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RestApiRequestTest {
  private final Context context = mock(Context.class);
  private final ParameterMetadata<String> STR_PARAM = new ParameterMetadata<>("str", STRING_TYPE);
  private final ParameterMetadata<Integer> INT_PARAM = new ParameterMetadata<>("int", INTEGER_TYPE);
  private final ParameterMetadata<Boolean> BOOL_PARAM =
      new ParameterMetadata<>("bool", BOOLEAN_TYPE);
  private final ParameterMetadata<Byte> BYTE_PARAM = new ParameterMetadata<>("byte", BYTE_TYPE);
  private final EndpointMetadata metadata =
      EndpointMetadata.get("/foo/:bool/:int/:str/:byte")
          .operationId("foo")
          .summary("Foo Summary")
          .description("description")
          .response(SC_OK, "Good")
          .pathParam(BOOL_PARAM)
          .pathParam(BYTE_PARAM)
          .pathParam(INT_PARAM)
          .pathParam(STR_PARAM)
          .queryParam(BOOL_PARAM)
          .queryParam(BYTE_PARAM)
          .queryParam(INT_PARAM)
          .queryParam(STR_PARAM)
          .build();

  @Test
  void shouldDeserializeStringFromParameters() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("str", "byeWorld"));
    when(context.queryParamMap()).thenReturn(Map.of("str", List.of("helloWorld")));
    final RestApiRequest request = new RestApiRequest(context, metadata);
    assertThat(request.getPathParameter(STR_PARAM)).isEqualTo("byeWorld");
    assertThat(request.getQueryParameter(STR_PARAM)).isEqualTo("helloWorld");
  }

  @Test
  void shouldDeserializeIntegerFromParameters() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("int", "1234"));
    when(context.queryParamMap()).thenReturn(Map.of("int", List.of("4321")));
    final RestApiRequest request = new RestApiRequest(context, metadata);
    assertThat(request.getPathParameter(INT_PARAM)).isEqualTo(1234);
    assertThat(request.getQueryParameter(INT_PARAM)).isEqualTo(4321);
  }

  @Test
  void shouldDeserializeBooleanFromParameters() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("bool", "true"));
    when(context.queryParamMap()).thenReturn(Map.of("bool", List.of("false")));
    final RestApiRequest request = new RestApiRequest(context, metadata);
    assertThat(request.getPathParameter(BOOL_PARAM)).isEqualTo(true);
    assertThat(request.getQueryParameter(BOOL_PARAM)).isEqualTo(false);
  }

  @Test
  void shouldDeserializeByteFromParameters() throws Exception {
    final byte b1 = 127;
    final byte b2 = 1;
    when(context.pathParamMap()).thenReturn(Map.of("byte", "0x7f"));
    when(context.queryParamMap()).thenReturn(Map.of("byte", List.of("0x01")));
    final RestApiRequest request = new RestApiRequest(context, metadata);
    assertThat(request.getPathParameter(BYTE_PARAM)).isEqualTo(b1);
    assertThat(request.getQueryParameter(BYTE_PARAM)).isEqualTo(b2);
  }
}
