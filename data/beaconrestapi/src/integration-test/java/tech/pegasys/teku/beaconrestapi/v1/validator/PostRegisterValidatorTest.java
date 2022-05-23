/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import java.io.IOException;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostRegisterValidator;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostRegisterValidatorTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void shouldReturnOk() throws IOException {
    final SszList<SignedValidatorRegistration> request =
        dataStructureUtil.randomValidatorRegistrations(10);

    when(validatorApiChannel.registerValidators(request)).thenReturn(SafeFuture.COMPLETE);

    Response response =
        post(
            PostRegisterValidator.ROUTE,
            JsonUtil.serialize(
                request, SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition()));

    Assertions.assertThat(response.code()).isEqualTo(SC_OK);
  }
}
