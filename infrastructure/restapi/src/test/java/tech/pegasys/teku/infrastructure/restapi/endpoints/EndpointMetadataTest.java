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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.SERIALIZABLE_ONE_OF_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TYPE_A;
import static tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition.TYPE_B;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata.EndpointMetaDataBuilder;
import tech.pegasys.teku.infrastructure.restapi.openapi.ContentTypeDefinition;

class EndpointMetadataTest {

  private static final ParameterMetadata<String> STRING_PARAM =
      new ParameterMetadata<>("t", STRING_TYPE);

  @Test
  void shouldGetAllReferencedTypeDefinitions() {
    final DeserializableTypeDefinition<String> describedStringType =
        CoreTypes.string("describedString");
    final SerializableTypeDefinition<String> objectType1 =
        SerializableTypeDefinition.object(String.class).name("Test1").build();
    final SerializableTypeDefinition<String> objectType2 =
        SerializableTypeDefinition.object(String.class).name("Test2").build();
    final SerializableTypeDefinition<String> objectType3 =
        SerializableTypeDefinition.object(String.class)
            .name("Test4")
            .withField("type3", objectType2, __ -> null)
            .build();
    final EndpointMetadata metadata =
        new EndpointMetaDataBuilder()
            .method(HandlerType.GET)
            .path("/foo")
            .summary("foo")
            .description("foo")
            .operationId("foo")
            .response(200, "foo", CoreTypes.HTTP_ERROR_RESPONSE_TYPE)
            .response(404, "foo", describedStringType)
            .response(
                500,
                "foo",
                Map.of(
                    "application/json", ContentTypeDefinition.json(objectType1),
                    "application/ssz", ContentTypeDefinition.json(objectType3)))
            .build();

    assertThat(metadata.getReferencedTypeDefinitions())
        .containsExactlyInAnyOrder(
            describedStringType,
            objectType1,
            objectType2,
            objectType3,
            CoreTypes.HTTP_ERROR_RESPONSE_TYPE,
            STRING_TYPE,
            CoreTypes.INTEGER_TYPE);
  }

  @Test
  void selectContentType_shouldUseDefaultContentTypeWhenRequestedType() {
    final String contentType = "application/foo";
    final EndpointMetadata metadata =
        validBuilder()
            .response(
                SC_OK, "Success", Map.of(contentType, ContentTypeDefinition.json(STRING_TYPE)))
            .defaultResponseType(contentType)
            .build();
    assertThat(metadata.selectContentType(SC_OK, Optional.empty())).isEqualTo(contentType);
  }

  @Test
  void selectContentType_shouldThrowExceptionWhenStatusCodeNotDeclared() {
    final EndpointMetadata metadata =
        validBuilder().response(SC_OK, "Success", STRING_TYPE).build();
    assertThatThrownBy(() -> metadata.selectContentType(SC_NOT_FOUND, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void selectContentType_shouldReturnDefaultContentTypeWhenRequestedContentTypeNotSupported() {
    final String contentType = "application/foo";
    final EndpointMetadata metadata =
        validBuilder()
            .response(
                SC_OK, "Success", Map.of(contentType, ContentTypeDefinition.json(STRING_TYPE)))
            .defaultResponseType(contentType)
            .build();
    assertThat(metadata.selectContentType(SC_OK, Optional.of("foo"))).isEqualTo(contentType);
  }

  @Test
  void selectContentType_shouldReturnRequestedTypeWhenAvailable() {
    final EndpointMetadata metadata =
        validBuilder()
            .response(
                SC_OK,
                "Success",
                Map.of(
                    ContentTypes.JSON,
                    ContentTypeDefinition.json(STRING_TYPE),
                    ContentTypes.OCTET_STREAM,
                    ContentTypeDefinition.json(STRING_TYPE)))
            .build();
    assertThat(metadata.selectContentType(SC_OK, Optional.of(ContentTypes.OCTET_STREAM)))
        .isEqualTo(ContentTypes.OCTET_STREAM);
  }

  @Test
  void selectContentType_shouldThrowExceptionWhenDefaultContentTypeNotSupported() {
    final EndpointMetadata metadata =
        validBuilder().response(SC_OK, "Foo").defaultResponseType("baa").build();
    assertThatThrownBy(() -> metadata.selectContentType(SC_OK, Optional.empty()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pathParam_shouldSerializeOpenApiDoc() throws IOException {
    final EndpointMetadata metadata =
        validBuilder()
            .pathParam(new ParameterMetadata<>("test", STRING_TYPE.withDescription("test2")))
            .queryParam(new ParameterMetadata<>("qtest", STRING_TYPE))
            .queryParamRequired(
                new ParameterMetadata<>("rq", INTEGER_TYPE.withDescription("testing")))
            .response(SC_OK, "Success", STRING_TYPE)
            .build();
    final JsonGenerator generator = mock(JsonGenerator.class);
    metadata.writeOpenApi(generator);
    verify(generator).writeArrayFieldStart("parameters");
  }

  @Test
  void queryParam_cannotSpecifyTwice() {
    assertThatThrownBy(() -> validBuilder().queryParam(STRING_PARAM).queryParam(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void queryParamRequired_cannotSpecifyTwice() {
    assertThatThrownBy(
            () -> validBuilder().queryParamRequired(STRING_PARAM).queryParamRequired(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void queryParamRequired_cannotSpecifyWithQueryParam() {
    assertThatThrownBy(
            () -> validBuilder().queryParam(STRING_PARAM).queryParamRequired(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void queryParam_cannotSpecifyWithQueryParamRequired() {
    assertThatThrownBy(
            () -> validBuilder().queryParamRequired(STRING_PARAM).queryParam(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pathParam_cannotSpecifyTwice() {
    assertThatThrownBy(() -> validBuilder().pathParam(STRING_PARAM).pathParam(STRING_PARAM))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pathParam_canSpecifyWithSameNameAsQueryParam() {
    assertThat(
            validBuilder()
                .pathParam(STRING_PARAM)
                .queryParam(STRING_PARAM)
                .response(SC_OK, "Success", STRING_TYPE)
                .build())
        .isInstanceOf(EndpointMetadata.class);
  }

  @Test
  void requestBodyType_shouldAcceptTypes() {
    final DeserializableTypeDefinition<String> type = STRING_TYPE;
    final EndpointMetadata metadata =
        validBuilder().requestBodyType(STRING_TYPE).response(SC_OK, "Success", STRING_TYPE).build();
    assertThat(metadata.getRequestBodyType()).isSameAs(type);
  }

  @Test
  void shouldAddSecurityToEndpoint() {
    final EndpointMetadata metadata =
        validBuilder().security("authBearer").response(SC_OK, "Success", STRING_TYPE).build();
    assertThat(metadata.getSecurity()).contains("authBearer");
  }

  @Test
  void shouldAddTagsToEndpoint() {
    final EndpointMetadata metadata =
        validBuilder()
            .tags(RestApiConstants.TAG_EXPERIMENTAL)
            .response(SC_OK, "Success", STRING_TYPE)
            .build();
    assertThat(metadata.getTags()).containsExactly(RestApiConstants.TAG_EXPERIMENTAL);
  }

  @Test
  void shouldDeprecateEndpoint() throws Exception {
    final EndpointMetadata metadata =
        validBuilder().deprecated(true).response(SC_OK, "Success", STRING_TYPE).build();
    final JsonGenerator generator = mock(JsonGenerator.class);
    metadata.writeOpenApi(generator);
    verify(generator).writeBooleanField("deprecated", true);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void requestBodyType_shouldAcceptLists() {
    final DeserializableListTypeDefinition<String> type =
        new DeserializableListTypeDefinition(STRING_TYPE);
    final EndpointMetadata metadata =
        validBuilder().requestBodyType(type).response(SC_OK, "Success", STRING_TYPE).build();
    assertThat(metadata.getRequestBodyType()).isSameAs(type);
  }

  @Test
  void requestBodyType_shouldDetermineOneOf() {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(SERIALIZABLE_ONE_OF_TYPE_DEFINITION, this::selector)
            .response(SC_OK, "Success")
            .build();

    assertThat(metadata.getRequestBodyType("{\"value1\":\"FOO\"}")).isEqualTo(TYPE_A);
  }

  @Test
  void requestBody_shouldGetBodyAsObject() throws JsonProcessingException {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(SERIALIZABLE_ONE_OF_TYPE_DEFINITION, this::selector)
            .response(SC_OK, "Success")
            .build();
    final OneOfTypeTestTypeDefinition.TestObjA a = metadata.getRequestBody("{\"value1\":\"FOO\"}");
    assertThat(a).isEqualTo(new OneOfTypeTestTypeDefinition.TestObjA("FOO"));
  }

  @Test
  void requestBody_shouldMatchSameType() throws JsonProcessingException {
    final EndpointMetadata metadata =
        validBuilder().requestBodyType(TYPE_A).response(SC_OK, "Success").build();

    final OneOfTypeTestTypeDefinition.TestObjA a = metadata.getRequestBody("{\"value1\":\"FOO\"}");
    assertThat(a).isEqualTo(new OneOfTypeTestTypeDefinition.TestObjA("FOO"));
  }

  @Test
  void requestBodyType_shouldErrorIfSelectorDeterminesUndocumentedResult() {
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(SERIALIZABLE_ONE_OF_TYPE_DEFINITION, (__) -> STRING_TYPE)
            .response(SC_OK, "Success")
            .build();

    assertThatThrownBy(() -> metadata.getRequestBody("{\"value1\":\"FOO\"}"))
        .isInstanceOf(IllegalStateException.class);
  }

  private <T> DeserializableTypeDefinition<?> selector(final String jsonData) {
    final ObjectMapper mapper = new ObjectMapper();
    try {
      final JsonNode jsonNode = mapper.readTree(jsonData);
      if (jsonNode.has("value1")) {
        return TYPE_A;
      }
      if (jsonNode.has("value2")) {
        return TYPE_B;
      }
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not parse object to find one-of type information");
    }
    throw new IllegalStateException("Object type not found in one-of selector");
  }

  private EndpointMetaDataBuilder validBuilder() {
    return EndpointMetadata.get("/foo")
        .operationId("fooId")
        .summary("foo summary")
        .description("foo description");
  }
}
