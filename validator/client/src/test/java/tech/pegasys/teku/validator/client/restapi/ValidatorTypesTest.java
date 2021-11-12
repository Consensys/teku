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

package tech.pegasys.teku.validator.client.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.parse;
import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;

class ValidatorTypesTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void listKeysResponse_shouldFormatListOfPublicKeys() throws Exception {
    final List<BLSPublicKey> keys =
        List.of(
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomPublicKey());

    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(keys, ValidatorTypes.LIST_KEYS_RESPONSE_TYPE));

    assertThat(result).containsOnlyKeys("keys");
    final List<Map<String, Object>> keysList = JsonTestUtil.getList(result, "keys");
    assertThat(keysList).hasSize(keys.size());
    for (int i = 0; i < keys.size(); i++) {
      assertThat(keysList.get(i))
          .containsOnly(
              entry("validating_pubkey", keys.get(i).toBytesCompressed().toHexString()),
              entry("derivation_path", null));
    }
  }

  @Test
  void listKeysResponse_shouldSerializeOpenApi() throws Exception {
    assertOpenApi(ValidatorTypes.LIST_KEYS_RESPONSE_TYPE, "ListKeysResponse.json");
  }

  @Test
  void pubkey_shouldSerializeOpenApi() throws Exception {
    assertOpenApi(ValidatorTypes.PUBKEY_TYPE, "PubKey.json");
  }

  @Test
  void deleteKeysResponse_shouldSerializeOpenApi() throws Exception {
    assertOpenApi(ValidatorTypes.DELETE_KEYS_RESPONSE_TYPE, "DeleteKeysResponse.json");
  }

  @Test
  void deleteKeyResult_shouldSerializeOpenApi() throws Exception {
    assertOpenApi(ValidatorTypes.DELETE_KEY_RESULT, "DeleteKeyResult.json");
  }

  @Test
  void deleteKeysRequest_shouldDeserializeData() throws Exception {
    final List<BLSPublicKey> keys =
        List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    DeleteKeysRequest request = new DeleteKeysRequest();
    request.setPublicKeys(keys);
    assertRoundTrip(request, ValidatorTypes.DELETE_KEYS_REQUEST);
  }

  @Test
  void deleteKeysRequest_shouldDeserializeEmptyList() throws Exception {
    assertRoundTrip(new DeleteKeysRequest(), ValidatorTypes.DELETE_KEYS_REQUEST);
  }

  @Test
  void deleteKeysRequest_shouldSerializeOpenApi() throws Exception {
    assertOpenApi(ValidatorTypes.DELETE_KEYS_REQUEST, "DeleteKeysRequest.json");
  }

  private void assertOpenApi(final SerializableTypeDefinition<?> type, final String resourceName)
      throws Exception {
    final Map<String, Object> expectedParsed = parseJsonResource(resourceName);
    final String json = JsonUtil.serialize(type::serializeOpenApiType);
    final Map<String, Object> result = JsonTestUtil.parse(json);
    assertThat(result).isEqualTo(expectedParsed);
  }

  private Map<String, Object> parseJsonResource(final String resourceName) throws Exception {
    return JsonTestUtil.parseJsonResource(ValidatorTypesTest.class, resourceName);
  }

  private <T> void assertRoundTrip(final T value, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    final T result = parse(serialize(value, type), type);
    assertThat(result).isEqualTo(value);
  }
}
