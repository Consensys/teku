/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.loader;

import static java.nio.file.Files.createTempFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import tech.pegasys.techu.service.serviceutils.layout.SimpleDataDirLayout;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;

public class ExternalValidatorSourceTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final PublicKeyLoader publicKeyLoader = mock(PublicKeyLoader.class);
  private final HttpClient httpClient = mock(HttpClient.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final AsyncRunner asyncRunner = new StubAsyncRunner();
  private ThrottlingTaskQueue externalSignerTaskQueue;

  private final Supplier<HttpClient> httpClientFactory = () -> httpClient;

  @SuppressWarnings("unchecked")
  private final HttpResponse<Void> httpResponse = mock(HttpResponse.class);

  private ValidatorConfig config;
  private ValidatorSource validatorSource;

  public ExternalValidatorSourceTest() {}

  @BeforeEach
  void setup(@TempDir Path tempDir) throws IOException, InterruptedException {
    config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(new URL("http://localhost:9000"))
            .build();
    externalSignerTaskQueue =
        new ThrottlingTaskQueue(
            config.getValidatorExternalSignerConcurrentRequestLimit(),
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "external_signer_request_queue_size");
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
        .thenReturn(httpResponse);
    validatorSource =
        ExternalValidatorSource.create(
            spec,
            metricsSystem,
            config,
            () -> httpClient,
            publicKeyLoader,
            asyncRunner,
            true,
            externalSignerTaskQueue,
            Optional.of(new SimpleDataDirLayout(tempDir)));
  }

  @Test
  void shouldThrowExceptionWhenAddValidator() {
    assertThatThrownBy(() -> validatorSource.addValidator(null, "pass", BLSPublicKey.empty()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldReturnErrorWhenDeleteReadOnlyValidator() {
    assertThat(validatorSource.deleteValidator(BLSPublicKey.empty()))
        .isEqualTo(
            DeleteKeyResult.error(
                "Cannot delete validator from read-only local validator source."));
  }

  @Test
  void shouldSayFalseToAddValidators() {
    assertThat(validatorSource.canUpdateValidators()).isFalse();
  }

  @Test
  void addValidator_shouldGetConfigUrlWhenNotProvided(@TempDir Path tempDir) throws IOException {
    BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final AddValidatorResult result =
        getResultFromAddingValidator(tempDir, publicKey, Optional.empty());
    assertImportedSuccessfully(result, config.getValidatorExternalSignerUrl());

    assertFileContent(tempDir, publicKey, "{\"pubkey\":\"" + publicKey + "\"}");
  }

  @Test
  void addValidator_shouldGetUrlIfProvided(@TempDir Path tempDir) throws IOException {
    BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    URL url = new URL("http://host.com");
    final AddValidatorResult result =
        getResultFromAddingValidator(tempDir, publicKey, Optional.of(url));
    assertImportedSuccessfully(result, url);

    assertFileContent(
        tempDir, publicKey, "{\"pubkey\":\"" + publicKey + "\",\"url\":\"http://host.com\"}");
  }

  @Test
  void addValidator_shouldDetectDuplicate(@TempDir Path tempDir) throws IOException {
    BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final AddValidatorResult result1 =
        getResultFromAddingValidator(tempDir, publicKey, Optional.empty());
    assertImportedSuccessfully(result1, config.getValidatorExternalSignerUrl());

    final AddValidatorResult result2 =
        getResultFromAddingValidator(tempDir, publicKey, Optional.empty());
    assertThat(result2.getResult().getImportStatus()).isEqualTo(ImportStatus.DUPLICATE);
    assertThat(result2.getSigner()).isEmpty();

    assertFileContent(tempDir, publicKey, "{\"pubkey\":\"" + publicKey + "\"}");
  }

  @Test
  void shouldLoadExternalValidators(@TempDir Path tempDir) throws IOException {
    final DataDirLayout dataDirLayout = new SimpleDataDirLayout(tempDir);
    final ExternalValidatorSource externalValidatorSource =
        ExternalValidatorSource.create(
            spec,
            metricsSystem,
            config,
            httpClientFactory,
            publicKeyLoader,
            asyncRunner,
            false,
            externalSignerTaskQueue,
            Optional.of(dataDirLayout));

    final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
    createRemoteKeyFile(dataDirLayout, publicKey1, Optional.of(new URL("http://host.com")));

    final BLSPublicKey publicKey2 = dataStructureUtil.randomPublicKey();
    createRemoteKeyFile(dataDirLayout, publicKey2, Optional.empty());

    final List<ValidatorSource.ValidatorProvider> validators =
        externalValidatorSource.getAvailableValidators();

    final List<ValidatorProviderInfo> result =
        validators.stream()
            .map(
                v -> {
                  assertThat(v).isInstanceOf(ExternalValidatorProvider.class);
                  ExternalValidatorProvider provider = (ExternalValidatorProvider) v;
                  return new ValidatorProviderInfo(
                      provider.getPublicKey(), provider.getExternalSignerUrl());
                })
            .collect(Collectors.toList());

    assertThat(result)
        .containsExactlyInAnyOrder(
            new ValidatorProviderInfo(publicKey1, new URL("http://host.com")),
            new ValidatorProviderInfo(publicKey2, new URL("http://localhost:9000")));
  }

  private void createRemoteKeyFile(
      DataDirLayout dataDirLayout, BLSPublicKey publicKey, Optional<URL> url) throws IOException {
    final String urlContent = url.map(value -> ",\"url\":\"" + value + "\"").orElse("");
    final String CONTENT = "{\"pubkey\":\"" + publicKey + "\"" + urlContent + "}";

    final Path directory = ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout);
    directory.toFile().mkdirs();

    Path tempFile =
        createTempFile(directory, publicKey.toBytesCompressed().toUnprefixedHexString(), ".json");
    Files.write(CONTENT.getBytes(StandardCharsets.UTF_8), tempFile.toFile());
  }

  private AddValidatorResult getResultFromAddingValidator(
      final Path tempDir, final BLSPublicKey publicKey, final Optional<URL> signerUrl) {
    final ExternalValidatorSource externalValidatorSource =
        ExternalValidatorSource.create(
            spec,
            metricsSystem,
            config,
            httpClientFactory,
            publicKeyLoader,
            asyncRunner,
            false,
            externalSignerTaskQueue,
            Optional.of(new SimpleDataDirLayout(tempDir)));
    return externalValidatorSource.addValidator(publicKey, signerUrl);
  }

  private void assertFileContent(Path tempDir, BLSPublicKey publicKey, String expectedContent)
      throws IOException {
    String fileName = publicKey.toBytesCompressed().toUnprefixedHexString() + ".json";
    Path path =
        ValidatorClientService.getManagedRemoteKeyPath(new SimpleDataDirLayout(tempDir))
            .resolve(fileName);
    assertThat(path.toFile()).exists();
    assertThat(Files.toString(path.toFile(), Charsets.UTF_8)).isEqualTo(expectedContent);
  }

  private void assertImportedSuccessfully(AddValidatorResult result, URL expectedUrl) {
    assertThat(result.getResult().getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertThat(result.getSigner()).isNotEmpty();
    assertThat(result.getSigner().get().getSigningServiceUrl()).isNotEmpty();
    assertThat(result.getSigner().get().getSigningServiceUrl().get()).isEqualTo(expectedUrl);
  }

  static class ValidatorProviderInfo {
    private final BLSPublicKey publicKey;
    private final URL url;

    ValidatorProviderInfo(BLSPublicKey publicKey, URL url) {
      this.publicKey = publicKey;
      this.url = url;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ValidatorProviderInfo that = (ValidatorProviderInfo) o;
      return Objects.equals(publicKey, that.publicKey) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
      return Objects.hash(publicKey, url);
    }
  }
}
