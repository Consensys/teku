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
import static tech.pegasys.teku.core.signatures.NoOpSigner.NO_OP_SIGNER;
import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.parse;
import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeysRequest;

class ValidatorTypesTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void listKeysResponse_shouldFormatListOfPublicKeys() throws Exception {
    final List<Validator> keys =
        List.of(
            new Validator(dataStructureUtil.randomPublicKey(), NO_OP_SIGNER, Optional::empty, true),
            new Validator(
                dataStructureUtil.randomPublicKey(), NO_OP_SIGNER, Optional::empty, false));

    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(keys, ValidatorTypes.LIST_KEYS_RESPONSE_TYPE));

    assertThat(result).containsOnlyKeys("data");
    final List<Map<String, Object>> keysList = JsonTestUtil.getList(result, "data");
    assertThat(keysList).hasSize(keys.size());
    for (int i = 0; i < keys.size(); i++) {
      assertThat(keysList.get(i))
          .containsOnly(
              entry(
                  "validating_pubkey",
                  keys.get(i).getPublicKey().toBytesCompressed().toHexString()),
              entry("readonly", keys.get(i).isReadOnly()));
    }
  }

  @Test
  void postKeysRequest_shouldFormatPostKeysRequest() throws Exception {
    final PostKeysRequest request =
        new PostKeysRequest(List.of("key"), List.of("pass"), Optional.of("slashingprotection"));
    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(request, ValidatorTypes.POST_KEYS_REQUEST));
    assertThat(result)
        .containsOnly(
            entry("keystores", List.of("key")),
            entry("passwords", List.of("pass")),
            entry("slashing_protection", "slashingprotection"));
  }

  @Test
  void postKeysRequest_shouldFormatPostKeysRequestWithoutSlashingProtection() throws Exception {
    final PostKeysRequest request =
        new PostKeysRequest(List.of("key"), List.of("pass"), Optional.empty());
    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(request, ValidatorTypes.POST_KEYS_REQUEST));
    assertThat(result)
        .containsOnly(entry("keystores", List.of("key")), entry("passwords", List.of("pass")));
  }

  @Test
  void postKeysResponse_shouldFormatPostKeysResponse() throws Exception {
    final List<PostKeyResult> original =
        List.of(PostKeyResult.success(), PostKeyResult.error("message"), PostKeyResult.duplicate());
    final Map<String, Object> result =
        JsonTestUtil.parse(JsonUtil.serialize(original, ValidatorTypes.POST_KEYS_RESPONSE));
    assertThat(result.keySet()).containsOnly("data");
    final List<Map<String, Object>> keysList = JsonTestUtil.getList(result, "data");
    for (int i = 0; i < original.size(); i++) {
      if (original.get(i).getMessage().isPresent()) {
        assertThat(keysList.get(i))
            .containsOnly(
                entry("status", original.get(i).getImportStatus().toString()),
                entry("message", original.get(i).getMessage().orElseThrow()));
      } else {
        assertThat(keysList.get(i))
            .containsOnly(entry("status", original.get(i).getImportStatus().toString()));
      }
    }
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
  void deleteKeysResponse_shouldSerialize() throws Exception {
    final String slashString = "\"{}\"";
    final List<DeleteKeyResult> original =
        List.of(
            DeleteKeyResult.success(),
            DeleteKeyResult.error("ERR"),
            DeleteKeyResult.notActive(),
            DeleteKeyResult.notFound());
    final Map<String, Object> result =
        JsonTestUtil.parse(
            JsonUtil.serialize(
                new DeleteKeysResponse(original, slashString),
                ValidatorTypes.DELETE_KEYS_RESPONSE_TYPE));
    final List<Map<String, Object>> keysList = JsonTestUtil.getList(result, "data");
    for (int i = 0; i < original.size(); i++) {
      if (original.get(i).getMessage().isPresent()) {
        assertThat(keysList.get(i))
            .containsOnly(
                entry("status", original.get(i).getStatus().toString()),
                entry("message", original.get(i).getMessage().orElseThrow()));
      } else {
        assertThat(keysList.get(i))
            .containsOnly(entry("status", original.get(i).getStatus().toString()));
      }
    }
    assertThat(result.get("slashing_protection")).isEqualTo(slashString);
  }

  @Test
  void deleteKeysRequest_shouldDeserializeEmptyList() throws Exception {
    assertRoundTrip(new DeleteKeysRequest(), ValidatorTypes.DELETE_KEYS_REQUEST);
  }

  private <T> void assertRoundTrip(final T value, final DeserializableTypeDefinition<T> type)
      throws JsonProcessingException {
    final T result = parse(serialize(value, type), type);
    assertThat(result).isEqualTo(value);
  }
}
