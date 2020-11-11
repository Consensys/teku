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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class SignedBlockTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  final UInt64 slot = UInt64.MAX_VALUE;
  final Bytes32 signingRoot =
      Bytes32.fromHexString("0x6e2c5d8a89dfe121a92c8812bea69fe9f84ae48f63aafe34ef7e18c7eac9af70");
  private final String jsonData =
      Resources.toString(Resources.getResource("signedBlock.json"), StandardCharsets.UTF_8);

  public SignedBlockTest() throws IOException {}

  @Test
  public void shouldSerialize() throws JsonProcessingException {
    final SignedBlock signedBlock = new SignedBlock(slot, signingRoot);
    String str = jsonProvider.objectToPrettyJSON(signedBlock);
    assertThat(str).isEqualToNormalizingNewlines(jsonData);
  }

  @Test
  public void shouldDeserialize() throws JsonProcessingException {
    final SignedBlock signedBlock = jsonProvider.jsonToObject(jsonData, SignedBlock.class);
    assertThat(signedBlock.slot).isEqualTo(slot);
    assertThat(signedBlock.signingRoot).isEqualTo(signingRoot);
  }
}
