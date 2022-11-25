/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlsToExecutionChanges;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetBlsToExecutionChangesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void beforeEach() {
    spec = TestSpecFactory.createMinimalCapella();
    dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.CAPELLA);
  }

  @Test
  void getBlsToExecutionChangesFromPoolReturnsOk() throws IOException {
    final Set<SignedBlsToExecutionChange> expectedOperations =
        Set.of(
            dataStructureUtil.randomSignedBlsToExecutionChange(),
            dataStructureUtil.randomSignedBlsToExecutionChange(),
            dataStructureUtil.randomSignedBlsToExecutionChange());

    when(blsToExecutionChangePool.getAll()).thenReturn(expectedOperations);

    Response response = getResponse(GetBlsToExecutionChanges.ROUTE);

    assertThat(response.code()).isEqualTo(SC_OK);

    final List<SignedBlsToExecutionChange> operationsInResponse = readListFromResponse(response);
    assertThat(operationsInResponse).hasSize(expectedOperations.size());
    assertThat(operationsInResponse).hasSameElementsAs(expectedOperations);
  }

  private List<SignedBlsToExecutionChange> readListFromResponse(final Response response)
      throws IOException {
    final JsonNode body = jsonProvider.jsonToObject(response.body().string(), JsonNode.class);
    final CollectionType collectionType =
        jsonProvider
            .getObjectMapper()
            .getTypeFactory()
            .constructCollectionType(
                List.class, tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange.class);

    final List<tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange> data =
        jsonProvider.getObjectMapper().treeToValue(body.get("data"), collectionType);

    return data.stream()
        .map(op -> op.asInternalSignedBlsToExecutionChange(spec.getGenesisSpec()))
        .collect(Collectors.toList());
  }
}
