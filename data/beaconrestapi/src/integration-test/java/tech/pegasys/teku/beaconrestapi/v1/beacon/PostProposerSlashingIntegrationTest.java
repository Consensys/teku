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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostProposerSlashing;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostProposerSlashingIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    Response response =
        post(PostProposerSlashing.ROUTE, jsonProvider.objectToJSON("{\"foo\": \"bar\"}"));
    assertThat(response.code()).isEqualTo(400);
  }

  @Test
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final tech.pegasys.teku.datastructures.operations.ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing();

    final ProposerSlashing schemaSlashing = new ProposerSlashing(slashing);

    doThrow(new RuntimeException()).when(proposerSlashingPool).add(slashing);

    Response response = post(PostProposerSlashing.ROUTE, jsonProvider.objectToJSON(schemaSlashing));
    assertThat(response.code()).isEqualTo(500);
  }

  @Test
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    final tech.pegasys.teku.datastructures.operations.ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing();

    final ProposerSlashing schemaSlashing = new ProposerSlashing(slashing);

    when(proposerSlashingPool.add(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    Response response = post(PostProposerSlashing.ROUTE, jsonProvider.objectToJSON(schemaSlashing));

    verify(proposerSlashingPool).add(slashing);

    assertThat(response.code()).isEqualTo(200);
  }
}
