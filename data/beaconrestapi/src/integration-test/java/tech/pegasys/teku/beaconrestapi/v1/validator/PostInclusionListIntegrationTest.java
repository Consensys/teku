/*
 * Copyright Consensys Software Inc., 2025
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostInclusionList;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostInclusionListIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.EIP7805);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void shouldReturnOk() throws IOException {
    final SignedInclusionList signedInclusionList = dataStructureUtil.randomSignedInclusionList();
    when(validatorApiChannel.sendSignedInclusionLists(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    final SignedInclusionList request = signedInclusionList;

    try (Response response =
        post(
            PostInclusionList.ROUTE,
            JsonUtil.serialize(request, signedInclusionList.getSchema().getJsonTypeDefinition()),
            Optional.of("eip7805"))) {

      assertThat(response.code()).isEqualTo(SC_OK);
    }
  }

  @Test
  void postEmptyBodyShouldReturnBadRequest() throws IOException {

    try (Response response = post(PostInclusionList.ROUTE, "", Optional.of("eip7805"))) {

      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      verifyNoInteractions(validatorApiChannel);
    }
  }

  @Test
  void postInvalidBodyShouldReturnBadRequest() throws IOException {
    try (Response response =
        post(PostInclusionList.ROUTE, "invalid body", Optional.of("eip7805"))) {

      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      verifyNoInteractions(validatorApiChannel);
    }
  }
}
