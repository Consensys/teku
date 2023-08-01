/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.NoOpKeyManager;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetectionAlert;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApi;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;

public class PostVoluntaryExitIntegrationTest {

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final String route = "/eth/v1/validator/{pubkey}/voluntary_exit";

  private RestApi validatorRestApi;

  protected OkHttpClient client;

  private String password;

  @BeforeEach
  void setup(@TempDir Path tempDir) throws IOException {
    final DataDirLayout dir =
        DataDirLayout.createFrom(DataConfig.builder().dataBasePath(tempDir).build());

    final ValidatorRestApiConfig validatorRestApiConfig =
        ValidatorRestApiConfig.builder().restApiEnabled(true).restApiSslEnabled(false).build();
    client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build();

    validatorRestApi =
        ValidatorRestApi.create(
            validatorRestApiConfig,
            Optional.empty(),
            new NoOpKeyManager(),
            dir,
            Optional.empty(),
            new DoppelgangerDetectionAlert());
    validatorRestApi.start();
    LOG.info("listening on {}", () -> validatorRestApi.getListenPort());
    password =
        Files.readString(
            dir.getValidatorDataDirectory().resolve("key-manager/validator-api-bearer"));
  }

  @AfterEach
  void teardown() {
    validatorRestApi.stop();
  }

  @Test
  void requestWithCorrectPasswordReturnsNotImmplemented() throws IOException {
    Response response =
        post(route.replace("{pubkey}", dataStructureUtil.randomPublicKey().toString()), password);
    assertThat(response.code()).isEqualTo(SC_NOT_IMPLEMENTED);
    LOG.info("Done");
  }

  @Test
  void requestFailsWithoutCorrectPassword() throws IOException {
    Response response =
        post(route.replace("{pubkey}", dataStructureUtil.randomPublicKey().toString()), "eh.");
    assertThat(response.code()).isEqualTo(401);
    LOG.info("Done");
  }

  protected Response post(final String route, final String bearerString) throws IOException {
    final RequestBody body = RequestBody.create("", JSON);
    final Request request =
        new Request.Builder()
            .url(getUrl() + route)
            .addHeader("Authorization", "Bearer " + bearerString)
            .post(body)
            .build();
    return client.newCall(request).execute();
  }

  private String getUrl() {
    return "http://localhost:" + validatorRestApi.getListenPort();
  }
}
