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

public class SignedAttestationTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  final UInt64 target = UInt64.valueOf(1024);
  final UInt64 source = UInt64.valueOf(2048);
  final Bytes32 signingRoot =
      Bytes32.fromHexString("0x6e2c5d8a89dfe121a92c8812bea69fe9f84ae48f63aafe34ef7e18c7eac9af70");
  final String jsonData =
      Resources.toString(Resources.getResource("signedAttestation.json"), StandardCharsets.UTF_8);

  public SignedAttestationTest() throws IOException {}

  @Test
  public void shouldCreate() {
    final SignedAttestation signedAttestation = new SignedAttestation(source, target, signingRoot);
    assertThat(signedAttestation.sourceEpoch).isEqualTo(source);
    assertThat(signedAttestation.targetEpoch).isEqualTo(target);
    assertThat(signedAttestation.signingRoot).isEqualTo(signingRoot);
  }

  @Test
  public void shouldSerialize() throws JsonProcessingException {
    final SignedAttestation signedAttestation = new SignedAttestation(source, target, signingRoot);
    String str = jsonProvider.objectToPrettyJSON(signedAttestation);
    assertThat(str).isEqualTo(jsonData);
  }

  @Test
  public void shouldDeserialize() throws JsonProcessingException {
    final SignedAttestation signedAttestation =
        jsonProvider.jsonToObject(jsonData, SignedAttestation.class);
    assertThat(signedAttestation.sourceEpoch).isEqualTo(source);
    assertThat(signedAttestation.targetEpoch).isEqualTo(target);
    assertThat(signedAttestation.signingRoot).isEqualTo(signingRoot);
  }
}
