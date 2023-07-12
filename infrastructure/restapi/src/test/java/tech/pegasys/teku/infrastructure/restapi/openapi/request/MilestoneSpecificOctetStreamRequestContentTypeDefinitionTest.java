/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.infrastructure.restapi.openapi.request;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class MilestoneSpecificOctetStreamRequestContentTypeDefinitionTest {

  final byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
  final InputStream input = new ByteArrayInputStream(data);

  private final RequestContentTypeDefinition<String> typeDefinition =
      MilestoneSpecificOctetStreamRequestContentTypeDefinition.parseBytes(
          dummyParseBytesFunction());

  @Test
  public void whenDeserializingWithoutHeaderShouldIgnoreMilestoneInResult() throws Exception {
    final String result = typeDefinition.deserialize(input);
    assertThat(result).isEqualTo("hello_");
  }

  @Test
  public void whenDeserializingWithHeaderShouldUseIncludeMilestoneInResult() throws Exception {
    final String result =
        typeDefinition.deserialize(input, Map.of(HEADER_CONSENSUS_VERSION, "deneb"));
    assertThat(result).isEqualTo("hello_deneb");
  }

  private static BiFunction<Bytes, Optional<String>, String> dummyParseBytesFunction() {
    return (bytes, version) -> {
      final StringBuilder sb = new StringBuilder();

      sb.append(new String(bytes.toArray(), StandardCharsets.UTF_8));
      sb.append("_");
      version.ifPresent(sb::append);

      return sb.toString();
    };
  }
}
