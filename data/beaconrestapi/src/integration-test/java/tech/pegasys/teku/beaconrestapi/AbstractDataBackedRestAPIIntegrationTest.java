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

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.InMemoryStorageSystem;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.util.config.StateStorageMode;
import tech.pegasys.teku.util.config.TekuConfiguration;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public abstract class AbstractDataBackedRestAPIIntegrationTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);
  private static final okhttp3.MediaType JSON =
      okhttp3.MediaType.parse("application/json; charset=utf-8");
  private static final TekuConfiguration CONFIG =
      TekuConfiguration.builder()
          .setRestApiPort(0)
          .setRestApiDocsEnabled(false)
          .setRestApiHostWhitelist(List.of("127.0.0.1", "localhost"))
          .build();

  protected static final UnsignedLong SIX = UnsignedLong.valueOf(6);
  protected static final UnsignedLong SEVEN = UnsignedLong.valueOf(7);
  protected static final UnsignedLong EIGHT = UnsignedLong.valueOf(8);
  protected static final UnsignedLong NINE = UnsignedLong.valueOf(9);
  protected static final UnsignedLong TEN = UnsignedLong.valueOf(10);

  // Mocks
  protected final P2PNetwork<?> p2PNetwork = mock(P2PNetwork.class);
  protected final SyncService syncService = mock(SyncService.class);
  protected final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private InMemoryStorageSystem storageSystem;

  protected RecentChainData recentChainData;
  protected CombinedChainDataClient combinedChainDataClient;

  // Update utils
  private ChainBuilder chainBuilder;
  private ChainUpdater chainUpdater;

  protected final JsonProvider jsonProvider = new JsonProvider();
  private BlockImporter blockImporter;

  protected DataProvider dataProvider;
  protected BeaconRestApi beaconRestApi;

  protected OkHttpClient client;
  protected final ObjectMapper objectMapper = new ObjectMapper();

  private void setupStorage(final StateStorageMode storageMode) {
    setupStorage(InMemoryStorageSystem.createEmptyV3StorageSystem(storageMode));
  }

  private void setupStorage(final InMemoryStorageSystem storageSystem) {
    this.storageSystem = storageSystem;
    recentChainData = storageSystem.recentChainData();
    chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
    chainUpdater = new ChainUpdater(recentChainData, chainBuilder);
  }

  private void setupAndStartRestAPI(TekuConfiguration config) {
    blockImporter =
        new BlockImporter(recentChainData, mock(ForkChoice.class), storageSystem.eventBus());
    combinedChainDataClient = storageSystem.combinedChainDataClient();
    dataProvider =
        new DataProvider(
            recentChainData,
            combinedChainDataClient,
            p2PNetwork,
            syncService,
            validatorApiChannel,
            blockImporter);
    beaconRestApi = new BeaconRestApi(dataProvider, config);
    beaconRestApi.start();
    client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build();
  }

  private void setupAndStartRestAPI() {
    setupAndStartRestAPI(CONFIG);
  }

  protected void startPreForkChoiceRestAPI() {
    // Initialize genesis
    setupStorage(StateStorageMode.ARCHIVE);
    chainUpdater.initializeGenesis();
    // Restart storage system without running fork choice
    storageSystem = storageSystem.restarted(StateStorageMode.ARCHIVE);
    setupStorage(storageSystem);
    // Start API
    setupAndStartRestAPI();
  }

  protected void startPreGenesisRestAPI() {
    setupStorage(StateStorageMode.ARCHIVE);
    // Start API
    setupAndStartRestAPI();
  }

  protected void startPreGenesisRestAPIWithConfig(TekuConfiguration config) {
    setupStorage(StateStorageMode.ARCHIVE);
    // Start API
    setupAndStartRestAPI(config);
  }

  protected void startRestAPIAtGenesis() {
    startRestAPIAtGenesis(StateStorageMode.ARCHIVE);
  }

  protected void startRestAPIAtGenesis(final StateStorageMode storageMode) {
    // Initialize genesis
    setupStorage(storageMode);
    chainUpdater.initializeGenesis();
    // Start API
    setupAndStartRestAPI();
  }

  public List<SignedBlockAndState> createBlocksAtSlots(long... slots) {
    final UnsignedLong[] unsignedSlots =
        Arrays.stream(slots).mapToObj(UnsignedLong::valueOf).toArray(UnsignedLong[]::new);
    return createBlocksAtSlots(unsignedSlots);
  }

  public ArrayList<SignedBlockAndState> createBlocksAtSlots(UnsignedLong... slots) {
    final ArrayList<SignedBlockAndState> results = new ArrayList<>();
    for (UnsignedLong slot : slots) {
      final SignedBlockAndState block = chainUpdater.advanceChain(slot);
      chainUpdater.updateBestBlock(block);
      results.add(block);
    }
    return results;
  }

  public List<SignedBeaconBlock> createBlocksAtSlotsAndMapToApiResult(long... slots) {
    final UnsignedLong[] unsignedSlots =
        Arrays.stream(slots).mapToObj(UnsignedLong::valueOf).toArray(UnsignedLong[]::new);
    return createBlocksAtSlotsAndMapToApiResult(unsignedSlots);
  }

  public List<SignedBeaconBlock> createBlocksAtSlotsAndMapToApiResult(UnsignedLong... slots) {
    return createBlocksAtSlots(slots).stream()
        .map(SignedBlockAndState::getBlock)
        .map(SignedBeaconBlock::new)
        .collect(Collectors.toList());
  }

  public SignedBlockAndState finalizeChainAtEpoch(UnsignedLong epoch) {
    return chainUpdater.finalizeEpoch(epoch);
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

  protected void assertForbidden(final Response response) throws IOException {
    assertThat(response.code()).isEqualTo(SC_FORBIDDEN);
    assertThat(response.body().string()).contains("Host not authorized");
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
