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

package tech.pegasys.teku.beaconrestapi;

import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.spec.StubSpecProvider;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

/** @deprecated - use {@link AbstractDataBackedRestAPIIntegrationTest} */
@Deprecated
@SuppressWarnings("unchecked")
public abstract class AbstractBeaconRestAPIIntegrationTest {
  static final okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
  static final BeaconRestApiConfig restApiConfig =
      BeaconRestApiConfig.builder()
          .restApiPort(0)
          .restApiEnabled(true)
          .restApiDocsEnabled(false)
          .eth1DepositContractAddress(Eth1Address.ZERO)
          .restApiHostAllowlist(List.of("127.0.0.1", "localhost"))
          .build();

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  protected final ObjectMapper objectMapper = new ObjectMapper();

  protected final Eth2Network eth2Network = mock(Eth2Network.class);
  protected StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  protected RecentChainData recentChainData = mock(RecentChainData.class);
  protected final SyncService syncService = mock(SyncService.class);
  protected final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  private final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);
  protected final EventChannels eventChannels = mock(EventChannels.class);

  protected CombinedChainDataClient combinedChainDataClient =
      new CombinedChainDataClient(recentChainData, historicalChainData);
  protected DataProvider dataProvider;
  protected BeaconRestApi beaconRestApi;
  protected OkHttpClient client;

  @BeforeEach
  public void setup() {
    dataProvider =
        new DataProvider(
            StubSpecProvider.create(),
            recentChainData,
            combinedChainDataClient,
            eth2Network,
            syncService,
            validatorApiChannel,
            attestationPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool);

    beaconRestApi =
        new BeaconRestApi(dataProvider, restApiConfig, eventChannels, SyncAsyncRunner.SYNC_RUNNER);
    beaconRestApi.start();
    client = new OkHttpClient();
  }

  protected void assertNoContent(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEmpty();
  }

  protected void assertGone(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_GONE);
    assertThat(response.body().string()).isEmpty();
  }

  protected void assertNotFound(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.body().string()).isEmpty();
  }

  protected void assertBodyEquals(final Response response, final String body) throws IOException {
    assertThat(response.body().string()).isEqualTo(body);
  }

  protected Response getResponse(final String path) throws IOException {
    final String url = "http://localhost:" + beaconRestApi.getListenPort();
    final Request request = new Request.Builder().url(url + path).build();
    return client.newCall(request).execute();
  }

  protected Response getResponse(final String route, Map<String, String> getParams)
      throws IOException {
    final String params =
        getParams.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    return getResponse(route + "?" + params);
  }

  protected Response post(final String route, final String postData) throws IOException {
    System.out.println(postData);
    final RequestBody body = RequestBody.create(JSON, postData);
    final Request request = new Request.Builder().url(getUrl() + route).post(body).build();
    return client.newCall(request).execute();
  }

  protected String mapToJson(Map<String, Object> postParams) throws JsonProcessingException {
    return objectMapper.writer().writeValueAsString(postParams);
  }

  private String getUrl() {
    return "http://localhost:" + beaconRestApi.getListenPort();
  }

  @AfterEach
  public void tearDown() {
    beaconRestApi.stop();
  }
}
