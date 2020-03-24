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

package tech.pegasys.artemis.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.JsonBody.json;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import tech.pegasys.artemis.util.bls.BLS;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSecretKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

@ExtendWith(MockServerExtension.class)
public class ExternalMessageSignerServiceIntegrationTest {
  private static final String PRIVATE_KEY =
      "0x25295f0d1d592a90b333e26e85149708208e9f8e8bc18f6c77bd62f8ad7a6866";
  private static final int KEY_LENGTH = 48;
  private static final String UNKNOWN_PUBLIC_KEY =
      "0x989d34725a2bfc3f15105f3f5fc8741f436c25ee1ee4f948e425d6bcb8c56bce6e06c269635b7e985a7ffa639e2409bf";
  private static final int TIMEOUT_MS = 500;
  private static final Bytes SIGNING_ROOT = Bytes.fromHexString("0x42");
  private URI signingServiceUri;
  private ClientAndServer client;
  private BLSSignature expectedSignature;
  private ExternalMessageSignerService externalMessageSignerService;

  @BeforeEach
  void setup(final ClientAndServer client) throws URISyntaxException {
    this.client = client;
    signingServiceUri = new URI("http://127.0.0.1:" + client.getLocalPort());

    final Bytes privateKey = padLeft(Bytes.fromHexString(PRIVATE_KEY));
    final BLSKeyPair keyPair = new BLSKeyPair(BLSSecretKey.fromBytes(privateKey));
    expectedSignature = BLS.sign(keyPair.getSecretKey(), SIGNING_ROOT);

    externalMessageSignerService =
        new ExternalMessageSignerService(signingServiceUri, keyPair.getPublicKey(), TIMEOUT_MS);
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  @Test
  void failsSigningWhenSigningServiceReturnsFailureResponse() {
    final ExternalMessageSignerService externalMessageSignerService =
        new ExternalMessageSignerService(
            signingServiceUri,
            BLSPublicKey.fromBytes(Bytes.fromHexString(UNKNOWN_PUBLIC_KEY)),
            TIMEOUT_MS);

    assertThatThrownBy(() -> externalMessageSignerService.signBlock(SIGNING_ROOT).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(
            "External signer failed to sign and returned invalid response status code: 404");

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(UNKNOWN_PUBLIC_KEY, SIGNING_ROOT.toHexString());

    client.verify(
        HttpRequest.request()
            .withMethod("POST")
            .withBody(json(signingRequestBody))
            .withPath("/signer/block"));
  }

  @Test
  void failsSigningWhenSigningServiceTimesOut() {
    final int ensureTimeout = 5;
    client
        .when(HttpRequest.request().withMethod("POST").withPath("/signer/block"))
        .respond(
            HttpResponse.response().withDelay(TimeUnit.MILLISECONDS, TIMEOUT_MS + ensureTimeout));

    assertThatThrownBy(() -> externalMessageSignerService.signBlock(SIGNING_ROOT).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(
            "External signer failed to sign due to java.net.http.HttpTimeoutException: request timed out");
  }

  @Test
  void failsSigningWhenSigningServiceReturnsInvalidSignatureResponse() {
    client
        .when(HttpRequest.request().withMethod("POST").withPath("/signer/block"))
        .respond(HttpResponse.response().withBody("INVALID_RESPONSE"));

    assertThatThrownBy(() -> externalMessageSignerService.signBlock(SIGNING_ROOT).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(
            "External signer returned an invalid signature: Illegal character 'I' found at index 0 in hex binary representation");
  }

  @Test
  void signsBlockWhenSigningServiceReturnsSuccessfulResponse() {
    client
        .when(HttpRequest.request().withMethod("POST").withPath("/signer/block"))
        .respond(HttpResponse.response().withBody(expectedSignature.getSignature().toString()));

    final BLSSignature signature = externalMessageSignerService.signBlock(SIGNING_ROOT).join();
    assertThat(signature).isEqualTo(expectedSignature);
  }

  @Test
  void signsAttestationWhenSigningServiceReturnsSuccessfulResponse() {
    client
        .when(HttpRequest.request().withMethod("POST").withPath("/signer/attestation"))
        .respond(HttpResponse.response().withBody(expectedSignature.getSignature().toString()));

    final BLSSignature signature =
        externalMessageSignerService.signAttestation(SIGNING_ROOT).join();
    assertThat(signature).isEqualTo(expectedSignature);
  }

  @Test
  void signsRandaoRevealWhenSigningServiceReturnsSuccessfulResponse() {
    client
        .when(HttpRequest.request().withMethod("POST").withPath("/signer/randao_reveal"))
        .respond(HttpResponse.response().withBody(expectedSignature.getSignature().toString()));

    final BLSSignature signature =
        externalMessageSignerService.signRandaoReveal(SIGNING_ROOT).join();
    assertThat(signature).isEqualTo(expectedSignature);
  }

  private Bytes padLeft(Bytes input) {
    return Bytes.concatenate(Bytes.wrap(new byte[KEY_LENGTH - input.size()]), input);
  }
}
