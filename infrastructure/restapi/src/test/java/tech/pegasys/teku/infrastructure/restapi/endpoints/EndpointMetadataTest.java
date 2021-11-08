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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.JSON_CONTENT_TYPE;

import io.javalin.http.HandlerType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata.EndpointMetaDataBuilder;
import tech.pegasys.teku.infrastructure.restapi.types.CoreTypes;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableArrayTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;

class EndpointMetadataTest {
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
                    "application/json", objectType1,
                    "application/ssz", objectType3))
            .build();

    assertThat(metadata.getReferencedTypeDefinitions())
        .containsExactlyInAnyOrder(
            describedStringType,
            objectType1,
            objectType2,
            objectType3,
            CoreTypes.HTTP_ERROR_RESPONSE_TYPE,
            CoreTypes.STRING_TYPE,
            CoreTypes.INTEGER_TYPE);
  }

  @Test
  void getResponseType_shouldThrowExceptionWhenStatusCodeNodeDeclared() {
    final EndpointMetadata metadata =
        validBuilder().response(SC_OK, "Success", CoreTypes.STRING_TYPE).build();
    assertThatThrownBy(() -> metadata.getResponseType(SC_NOT_FOUND, JSON_CONTENT_TYPE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getResponseType_shouldThrowExceptionWhenStatusCodeMatchesButContentTypeNotDeclared() {
    final EndpointMetadata metadata =
        validBuilder().response(SC_OK, "Success", CoreTypes.STRING_TYPE).build();
    assertThatThrownBy(() -> metadata.getResponseType(SC_OK, "foo"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requestBodyType_shouldAcceptTypes() {
    final DeserializableTypeDefinition<String> type = CoreTypes.STRING_TYPE;
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(CoreTypes.STRING_TYPE)
            .response(SC_OK, "Success", CoreTypes.STRING_TYPE)
            .build();
    assertThat(metadata.getRequestBodyType()).isSameAs(type);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void requestBodyType_shouldAcceptLists() {
    final DeserializableArrayTypeDefinition<String> type =
        new DeserializableArrayTypeDefinition(CoreTypes.STRING_TYPE);
    final EndpointMetadata metadata =
        validBuilder()
            .requestBodyType(type)
            .response(SC_OK, "Success", CoreTypes.STRING_TYPE)
            .build();
    assertThat(metadata.getRequestBodyType()).isSameAs(type);
  }

  @Test
  void getResponseType_shouldGetDeclaredType() {
    final SerializableTypeDefinition<String> type = CoreTypes.STRING_TYPE;
    final EndpointMetadata metadata = validBuilder().response(SC_OK, "Success", type).build();
    assertThat(metadata.getResponseType(SC_OK, JSON_CONTENT_TYPE)).isSameAs(type);
  }

  private EndpointMetaDataBuilder validBuilder() {
    return EndpointMetadata.get("/foo")
        .operationId("fooId")
        .summary("foo summary")
        .description("foo description");
  }
}
