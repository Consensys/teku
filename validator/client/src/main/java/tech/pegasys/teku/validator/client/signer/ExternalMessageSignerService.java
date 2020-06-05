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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.MessageSignerService;
import tech.pegasys.teku.util.async.SafeFuture;

public class ExternalMessageSignerService implements MessageSignerService {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final URL signingServiceUrl;
  private final BLSPublicKey blsPublicKey;
  private final Duration timeout;

  public ExternalMessageSignerService(
      final URL signingServiceUrl, final BLSPublicKey blsPublicKey, final Duration timeout) {
    this.signingServiceUrl = signingServiceUrl;
    this.blsPublicKey = blsPublicKey;
    this.timeout = timeout;
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final Bytes signingRoot) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signAttestation(final Bytes signingRoot) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final Bytes signingRoot) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(final Bytes signingRoot) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signRandaoReveal(final Bytes signingRoot) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(final Bytes signingRoot) {
    return sign(signingRoot);
  }

  private SafeFuture<BLSSignature> sign(final Bytes signingRoot) {
    final String publicKey = blsPublicKey.getPublicKey().toString();
    return SafeFuture.ofComposed(
        () -> {
          final String requestBody = createSigningRequestBody(signingRoot);
          final URI uri = signingServiceUrl.toURI().resolve("/signer/sign/" + publicKey);
          final HttpRequest request =
              HttpRequest.newBuilder()
                  .uri(uri)
                  .timeout(timeout)
                  .POST(BodyPublishers.ofString(requestBody))
                  .build();
          return HttpClient.newHttpClient()
              .sendAsync(request, BodyHandlers.ofString())
              .handleAsync(this::getBlsSignature);
        });
  }

  private String createSigningRequestBody(final Bytes signingRoot) {
    final SigningRequestBody signingRequest = new SigningRequestBody(signingRoot.toHexString());
    try {
      return MAPPER.writeValueAsString(signingRequest);
    } catch (final JsonProcessingException e) {
      throw new ExternalSignerException("Unable to create external signing request", e);
    }
  }

  private BLSSignature getBlsSignature(
      final HttpResponse<String> response, final Throwable throwable) {
    if (throwable != null) {
      throw new ExternalSignerException(
          "External signer failed to sign due to " + throwable.getMessage(), throwable);
    }

    if (response.statusCode() != 200) {
      throw new ExternalSignerException(
          "External signer failed to sign and returned invalid response status code: "
              + response.statusCode());
    }

    try {
      final Bytes signature = Bytes.fromHexString(response.body());
      return BLSSignature.fromBytes(signature);
    } catch (final IllegalArgumentException e) {
      throw new ExternalSignerException(
          "External signer returned an invalid signature: " + e.getMessage(), e);
    }
  }
}
