/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.PostAttesterSlashingV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class PostAttesterSlashingV2IntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalElectra());

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    final Response response = post(PostAttesterSlashingV2.ROUTE, jsonProvider.objectToJSON(""));
    Assertions.assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    final Response response =
        post(PostAttesterSlashingV2.ROUTE, jsonProvider.objectToJSON("{\"foo\": \"bar\"}"));
    assertThat(response.code()).isEqualTo(400);
  }

  @Test
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing slashing =
        dataStructureUtil.randomAttesterSlashing();

    final AttesterSlashing schemaSlashing = new AttesterSlashing(slashing);

    doThrow(new RuntimeException()).when(attesterSlashingPool).addLocal(slashing);

    final Response response =
        post(PostAttesterSlashingV2.ROUTE, jsonProvider.objectToJSON(schemaSlashing));
    assertThat(response.code()).isEqualTo(500);
  }

  @Test
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    final tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing slashing =
        dataStructureUtil.randomAttesterSlashing();

    final AttesterSlashing schemaSlashing = new AttesterSlashing(slashing);

    when(attesterSlashingPool.addLocal(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final Response response =
        post(PostAttesterSlashingV2.ROUTE, jsonProvider.objectToJSON(schemaSlashing));

    verify(attesterSlashingPool).addLocal(slashing);

    assertThat(response.code()).isEqualTo(200);
  }
}
