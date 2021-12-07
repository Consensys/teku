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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import com.google.common.io.Resources;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpResponse;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;

@ExtendWith(MockServerExtension.class)
public class ExternalSignerUpcheckTLSIntegrationTest {
  private static final BLSKeyPair KEYPAIR = BLSTestUtil.randomKeyPair(1234);
  private static final Duration TIMEOUT = Duration.ofMillis(500);
  private static final Path TEKU_KEYSTORE;
  private static final Path EXTERNAL_SIGNER_TRUSTSTORE;
  private static final Path PASSWORD_FILE;
  private static final Path TEKU_CLIENT_PEM;

  static {
    try {
      // MockServer (representing external signer) CA certificate is imported from:
      // https://github.com/mock-server/mockserver/blob/master/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem
      EXTERNAL_SIGNER_TRUSTSTORE = Path.of(Resources.getResource("mockito_truststore.p12").toURI());
      TEKU_KEYSTORE = Path.of(Resources.getResource("teku_client_keystore.p12").toURI());
      PASSWORD_FILE = Path.of(Resources.getResource("pass.txt").toURI());
      TEKU_CLIENT_PEM = Path.of(Resources.getResource("teku_client.pem").toURI());
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private ClientAndServer server;
  private ExternalSignerUpcheck externalSignerUpcheck;

  @BeforeAll
  static void mTLSSetup() {
    // Mock Server configuration for mutual authentication
    ConfigurationProperties.tlsMutualAuthenticationRequired(true);
    ConfigurationProperties.tlsMutualAuthenticationCertificateChain(TEKU_CLIENT_PEM.toString());
  }

  @BeforeEach
  void setup(final ClientAndServer server) throws MalformedURLException {
    this.server = server;
    this.server.withSecure(true);

    this.externalSignerUpcheck =
        buildExternalSignerUpcheck(new URL("https://127.0.0.1:" + server.getLocalPort()));
  }

  private static ExternalSignerUpcheck buildExternalSignerUpcheck(final URL serverUrl) {
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerPublicKeySources(List.of(KEYPAIR.getPublicKey().toString()))
            .validatorExternalSignerUrl(serverUrl)
            .validatorExternalSignerTimeout(TIMEOUT)
            .validatorExternalSignerKeystore(TEKU_KEYSTORE)
            .validatorExternalSignerKeystorePasswordFile(PASSWORD_FILE)
            .validatorExternalSignerTruststore(EXTERNAL_SIGNER_TRUSTSTORE)
            .validatorExternalSignerTruststorePasswordFile(PASSWORD_FILE)
            .build();

    final HttpClientExternalSignerFactory httpClientExternalSignerFactory =
        new HttpClientExternalSignerFactory(config);

    return new ExternalSignerUpcheck(
        httpClientExternalSignerFactory.get(),
        config.getValidatorExternalSignerUrl(),
        config.getValidatorExternalSignerTimeout());
  }

  @AfterEach
  void tearDown() {
    server.reset();
  }

  @Test
  void upcheckReturnsTrueWhenStatusCodeIs200() {
    server
        .when(request().withSecure(true).withMethod("GET").withPath("/upcheck"))
        .respond(HttpResponse.response().withStatusCode(200));

    assertThat(externalSignerUpcheck.upcheck()).isTrue();
  }

  @Test
  void upcheckReturnsFalseWhenStatusCodeIsNot200() {
    server
        .when(request().withSecure(true).withMethod("GET").withPath("/upcheck"))
        .respond(HttpResponse.response().withStatusCode(404));

    assertThat(externalSignerUpcheck.upcheck()).isFalse();
  }

  @Test
  void upcheckReturnsFalseWhenServerIsDown() throws MalformedURLException {
    final ExternalSignerUpcheck externalSignerUpcheck =
        buildExternalSignerUpcheck(new URL("https://127.0.0.2:79"));
    assertThat(externalSignerUpcheck.upcheck()).isFalse();
  }
}
