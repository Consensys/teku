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

package tech.pegasys.teku.validator.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

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
          .containsOnly(entry("validating_pubkey", keys.get(i).toBytesCompressed().toHexString()));
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
}
