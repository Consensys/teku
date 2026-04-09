/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.v4.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;

public class GetNewBlockV4Test extends AbstractMigratedBeaconHandlerTest {

  private final BLSSignature signature = BLSTestUtil.randomSignature(1234);

  @BeforeEach
  public void setup() {
    setHandler(new GetNewBlockV4(validatorDataProvider, schemaDefinitionCache));
    request.setPathParameter(SLOT, "1");
    request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());
  }

  @Test
  public void shouldReturnNotImplemented() throws JsonProcessingException {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_IMPLEMENTED);
  }

  @Test
  public void shouldHaveCorrectRoute() {
    assertThat(GetNewBlockV4.ROUTE).isEqualTo("/eth/v4/validator/blocks/{slot}");
  }

  @Test
  public void shouldHaveCorrectPath() {
    assertThat(handler.getMetadata().getPath()).isEqualTo(GetNewBlockV4.ROUTE);
  }
}
