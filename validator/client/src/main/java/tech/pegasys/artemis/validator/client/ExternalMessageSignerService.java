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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class ExternalMessageSignerService implements MessageSignerService {
  private static final Logger LOG = LogManager.getLogger();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private URI signingService;
  private BLSPublicKey blsPublicKey;
  private int timeoutMs;

  public ExternalMessageSignerService(
      final URI signingService, final BLSPublicKey blsPublicKey, final int timeoutMs) {
    this.signingService = signingService;
    this.blsPublicKey = blsPublicKey;
    this.timeoutMs = timeoutMs;
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final Bytes signingRoot) {
    return sign(signingRoot, "/signer/block");
  }

  @Override
  public SafeFuture<BLSSignature> signAttestation(final Bytes signingRoot) {
    return sign(signingRoot, "/signer/attestation");
  }

  @Override
  public SafeFuture<BLSSignature> signRandaoReveal(final Bytes signingRoot) {
    return sign(signingRoot, "/signer/randao_reveal");
  }

  private SafeFuture<BLSSignature> sign(final Bytes signingRoot, final String path) {
    final HttpClient httpClient = HttpClient.newHttpClient();

    final String publicKey = blsPublicKey.getPublicKey().toString();
    final SigningRequestBody signingRequest =
        new SigningRequestBody(publicKey, signingRoot.toHexString());
    final String requestBody;
    try {
      requestBody = MAPPER.writeValueAsString(signingRequest);
    } catch (JsonProcessingException e) {
      LOG.error("Unable to create external signing request", e);
      return SafeFuture.failedFuture(e);
    }

    final HttpRequest request;
    request =
        HttpRequest.newBuilder()
            .uri(signingService.resolve(path))
            .timeout(Duration.ofSeconds(timeoutMs))
            .POST(BodyPublishers.ofString(requestBody))
            .build();

    return SafeFuture.of(
        httpClient
            .sendAsync(request, BodyHandlers.ofString())
            .thenApply(response -> BLSSignature.fromBytes(Bytes.fromHexString(response.body()))));
  }
}
