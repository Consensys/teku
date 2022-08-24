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

package tech.pegasys.teku.beaconrestapi.tekuv1.beacon;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetDepositSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetDepositSnapshotIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  DepositTreeSnapshot depositTreeSnapshot;

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final List<Bytes32> finalized = new ArrayList<>();
    finalized.add(dataStructureUtil.randomBytes32());
    finalized.add(dataStructureUtil.randomBytes32());
    depositTreeSnapshot =
        new DepositTreeSnapshot(
            finalized,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomLong(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());
    when(eth1DataProvider.getFinalizedDepositTreeSnapshot())
        .thenReturn(Optional.of(depositTreeSnapshot));
  }

  @Test
  public void shouldReturnNotFoundWhenNoFinalizedTree() throws IOException {
    when(eth1DataProvider.getFinalizedDepositTreeSnapshot()).thenReturn(Optional.empty());
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnDepositTreeSnapshotJson() throws IOException {
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final String actualResponse = response.body().string();
    assertThat(actualResponse)
        .isEqualTo(
            JsonUtil.serialize(
                depositTreeSnapshot, GetDepositSnapshot.DEPOSIT_SNAPSHOT_RESPONSE_TYPE));
  }

  @Test
  public void shouldReturnDepositTreeSnapshotSsz() throws IOException {
    final Response response = getSsz();
    assertThat(response.code()).isEqualTo(SC_OK);
    final Bytes actualResponse = Bytes.wrap(response.body().bytes());
    assertThat(actualResponse).isEqualTo(depositTreeSnapshot.sszSerialize());
  }

  @Test
  public void shouldReturnServerErrorWhenProviderFails() throws IOException {
    when(eth1DataProvider.getFinalizedDepositTreeSnapshot()).thenThrow(new RuntimeException(""));
    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }

  private Response get() throws IOException {
    return getResponse(GetDepositSnapshot.ROUTE);
  }

  private Response getSsz() throws IOException {
    return getResponse(GetDepositSnapshot.ROUTE, ContentTypes.OCTET_STREAM);
  }
}
