/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT8_TYPE;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.restapi.CustomResponseTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;

public class RestApiRequestTest {
  private static final ParameterMetadata<String> STR_PARAM =
      new ParameterMetadata<>("str", STRING_TYPE);
  private static final ParameterMetadata<Integer> INT_PARAM =
      new ParameterMetadata<>("int", RAW_INTEGER_TYPE);
  private static final ParameterMetadata<Boolean> BOOL_PARAM =
      new ParameterMetadata<>("bool", BOOLEAN_TYPE);
  private static final ParameterMetadata<Byte> BYTE_PARAM =
      new ParameterMetadata<>("byte", BYTE_TYPE);
  private static final ParameterMetadata<Byte> UINT8_PARAM =
      new ParameterMetadata<>("uint8", UINT8_TYPE);
  private static final EndpointMetadata METADATA =
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

  private final Context context = mock(Context.class);

  @Test
  void shouldDeserializeStringFromParameters() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("str", "byeWorld"));
    when(context.queryParamMap()).thenReturn(Map.of("str", List.of("helloWorld")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(STR_PARAM)).isEqualTo("byeWorld");
    assertThat(request.getQueryParameter(STR_PARAM)).isEqualTo("helloWorld");
  }

  @Test
  void shouldDeserializeIntegerFromParameters() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("int", "1234"));
    when(context.queryParamMap()).thenReturn(Map.of("int", List.of("4321")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(INT_PARAM)).isEqualTo(1234);
    assertThat(request.getQueryParameter(INT_PARAM)).isEqualTo(4321);
  }

  @Test
  void shouldDeserializeBooleanFromParameters() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("bool", "true"));
    when(context.queryParamMap()).thenReturn(Map.of("bool", List.of("false")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(BOOL_PARAM)).isEqualTo(true);
    assertThat(request.getQueryParameter(BOOL_PARAM)).isEqualTo(false);
  }

  @ParameterizedTest
  @MethodSource("getContentTypeAndExpectedResponseType")
  void shouldReturnExpectedResponseType(
      final String contentType, final String expectedResponseType) {
    final ResponseContentTypeDefinition<Bytes32> responseContentTypeDefinition =
        new CustomResponseTypeDefinition<>(ContentTypes.OCTET_STREAM, BYTES32_TYPE);

    when(context.header(eq(HEADER_ACCEPT))).thenReturn(contentType);
    EndpointMetadata metadata =
        EndpointMetadata.get("/foo")
            .operationId("foo")
            .description("foobar")
            .summary("Foo Summary")
            .response(SC_OK, "Good", BYTES32_TYPE, responseContentTypeDefinition)
            .build();
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, metadata);
    assertThat(request.getResponseContentType(SC_OK)).isEqualTo(expectedResponseType);
  }

  public static Stream<Arguments> getContentTypeAndExpectedResponseType() {
    return Stream.of(
        Arguments.of(
            "application/octet-stream;q=0.9, application/json;q=0.4", "application/octet-stream"),
        Arguments.of("application/octet-stream;q=0.3, application/json;q=0.4", "application/json"),
        Arguments.of("application/json", "application/json"),
        Arguments.of("application/octet-stream", "application/octet-stream"),
        Arguments.of(null, "application/json"));
  }

  @ParameterizedTest
  @MethodSource("unsignedBytesToHex")
  void shouldDeserializeByteFromParameters(final byte value, final String stringValue) {
    when(context.pathParamMap()).thenReturn(Map.of("byte", stringValue));
    when(context.queryParamMap()).thenReturn(Map.of("byte", List.of(stringValue)));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(BYTE_PARAM)).isEqualTo(value);
    assertThat(request.getQueryParameter(BYTE_PARAM)).isEqualTo(value);
  }

  static Stream<Arguments> unsignedBytesToHex() {
    return Stream.of(
        Arguments.of(Byte.MIN_VALUE, "0x80"),
        Arguments.of((byte) -1, "0xff"),
        Arguments.of((byte) 0, "0x00"),
        Arguments.of((byte) 1, "0x01"),
        Arguments.of(Byte.MAX_VALUE, "0x7f"));
  }

  @ParameterizedTest
  @MethodSource("unsignedBytesToDecimal")
  void shouldDeserializeUInt8FromParameters(final byte value, final String stringValue) {
    when(context.pathParamMap()).thenReturn(Map.of("uint8", stringValue));
    when(context.queryParamMap()).thenReturn(Map.of("uint8", List.of(stringValue)));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);
    assertThat(request.getPathParameter(UINT8_PARAM)).isEqualTo(value);
    assertThat(request.getQueryParameter(UINT8_PARAM)).isEqualTo(value);
  }

  static Stream<Arguments> unsignedBytesToDecimal() {
    return Stream.of(
        Arguments.of(Byte.MIN_VALUE, "128"),
        Arguments.of((byte) -1, "255"),
        Arguments.of((byte) 0, "0"),
        Arguments.of((byte) 1, "1"),
        Arguments.of(Byte.MAX_VALUE, "127"));
  }

  @Test
  void shouldGetQueryParameterListTypeInteger() {
    when(context.queryParamMap()).thenReturn(Map.of("int", List.of("1", "2", "3")));
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);

    assertThat(request.getQueryParameterList(INT_PARAM)).isEqualTo(List.of(1, 2, 3));
  }

  @Test
  void shouldGetQueryParameterListWhenEmpty() {
    when(context.queryParamMap()).thenReturn(Map.of());
    final JavalinRestApiRequest request = new JavalinRestApiRequest(context, METADATA);

    assertThat(request.getQueryParameterList(INT_PARAM)).isEqualTo(List.of());
  }
}
