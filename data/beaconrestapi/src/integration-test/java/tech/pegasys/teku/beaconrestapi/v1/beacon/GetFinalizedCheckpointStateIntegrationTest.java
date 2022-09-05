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

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.IOException;
import java.util.function.Function;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetFinalizedCheckpointState;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;

public class GetFinalizedCheckpointStateIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetBellatrixStateAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetFinalizedCheckpointStateIntegrationTest.ResponseData<? extends BeaconState>
        stateResponse =
            JsonUtil.parse(
                response.body().string(),
                typeDefinition(
                    spec.forMilestone(SpecMilestone.BELLATRIX)
                        .getSchemaDefinitions()
                        .getBeaconStateSchema()));
    assertThat(stateResponse).isNotNull();
    assertThat(stateResponse.getVersion()).isEqualTo(SpecMilestone.BELLATRIX);
    assertThat(stateResponse.getData()).isInstanceOf(BeaconStateAltair.class);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.bellatrix.name());
  }

  @Test
  public void shouldGetBellatrixStateAsSsz() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.bellatrix.name());
  }

  public Response get() throws IOException {
    return getResponse(GetFinalizedCheckpointState.ROUTE);
  }

  private <T extends SszData>
      DeserializableTypeDefinition<GetFinalizedCheckpointStateIntegrationTest.ResponseData<T>>
          typeDefinition(final SszSchema<T> dataSchema) {
    return DeserializableTypeDefinition
        .<GetFinalizedCheckpointStateIntegrationTest.ResponseData<T>,
            GetFinalizedCheckpointStateIntegrationTest.ResponseData<T>>
            object()
        .initializer(GetFinalizedCheckpointStateIntegrationTest.ResponseData::new)
        .finisher(Function.identity())
        .withField(
            "version",
            DeserializableTypeDefinition.enumOf(SpecMilestone.class),
            GetFinalizedCheckpointStateIntegrationTest.ResponseData::getVersion,
            GetFinalizedCheckpointStateIntegrationTest.ResponseData::setVersion)
        .withField(
            "data",
            dataSchema.getJsonTypeDefinition(),
            GetFinalizedCheckpointStateIntegrationTest.ResponseData::getData,
            GetFinalizedCheckpointStateIntegrationTest.ResponseData::setData)
        .build();
  }

  private static class ResponseData<T> {
    private SpecMilestone version;
    private T data;

    public SpecMilestone getVersion() {
      return version;
    }

    public void setVersion(final SpecMilestone version) {
      this.version = version;
    }

    public T getData() {
      return data;
    }

    public void setData(final T data) {
      this.data = data;
    }
  }
}
