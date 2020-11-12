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

package tech.pegasys.teku.data.slashinginterchange;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class SigningHistoryTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ObjectMapper mapper = jsonProvider.getObjectMapper();
  private final BLSPubKey blsPubKey =
      BLSPubKey.fromHexString(
          "0xb845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed");
  private final Bytes32 GENESIS_ROOT =
      Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000123456");

  @Test
  public void shouldReadMetadataFromCompleteJson() throws IOException {
    final String minimalJson =
        Resources.toString(Resources.getResource("format1_complete.json"), StandardCharsets.UTF_8);

    JsonNode jsonNode = mapper.readTree(minimalJson);
    JsonNode metadataJson = jsonNode.get("metadata");
    Metadata metadata = mapper.treeToValue(metadataJson, Metadata.class);
    assertThat(metadata).isEqualTo(new Metadata(INTERCHANGE_VERSION, GENESIS_ROOT));

    List<SigningHistory> completeSigningHistories =
        Arrays.asList(mapper.readValue(jsonNode.get("data").toString(), SigningHistory[].class));

    assertThat(completeSigningHistories)
        .containsExactly(
            new SigningHistory(
                blsPubKey,
                List.of(
                    new SignedBlock(
                        UInt64.valueOf(81952),
                        Bytes32.fromHexString(
                            "0x0000000000000000000000000000000000000000000000000000000000001234"))),
                List.of(
                    new SignedAttestation(
                        UInt64.valueOf(2290),
                        UInt64.valueOf(3007),
                        Bytes32.fromHexString(
                            "0x0000000000000000000000000000000000000000000000000000000000000123")))));
  }
}
