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

package tech.pegasys.artemis.beaconrestapi;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public abstract class AbstractBeaconRestAPIIntegrationTest {
  private static final String THE_CONFIG =
      String.format("beaconrestapi.portNumber=%d\nbeaconrestapi.enableSwagger=%s", 0, "false");
  private static final okhttp3.MediaType JSON =
      okhttp3.MediaType.parse("application/json; charset=utf-8");

  private final ObjectMapper objectMapper = new ObjectMapper();
  protected final P2PNetwork<?> p2PNetwork = mock(P2PNetwork.class);
  protected final HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  protected final ChainStorageClient chainStorageClient = mock(ChainStorageClient.class);
  protected final CombinedChainDataClient combinedChainDataClient =
      new CombinedChainDataClient(chainStorageClient, historicalChainData);
  protected final SyncService syncService = mock(SyncService.class);
  protected final ValidatorCoordinator validatorCoordinator = mock(ValidatorCoordinator.class);

  private final DataProvider dataProvider =
      new DataProvider(
          chainStorageClient,
          combinedChainDataClient,
          p2PNetwork,
          syncService,
          validatorCoordinator);

  private BeaconRestApi beaconRestApi;
  protected OkHttpClient client;

  @BeforeEach
  public void setup() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString(THE_CONFIG);
    beaconRestApi = new BeaconRestApi(dataProvider, config);
    beaconRestApi.start();
    client = new OkHttpClient();
  }

  protected void assertNoContent(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEmpty();
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
