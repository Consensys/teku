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

import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForRandaoReveal;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAggregateAndProof;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAggregationSlot;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAttestationData;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignBlock;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignVoluntaryExit;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class ExternalSigner implements Signer {
  public static final String EXTERNAL_SIGNER_ENDPOINT = "/api/v1/eth2/sign";
  private static final String FORK_INFO = "fork_info";
  private final JsonProvider jsonProvider = new JsonProvider();
  private final URL signingServiceUrl;
  private final BLSPublicKey blsPublicKey;
  private final Duration timeout;
  private final HttpClient httpClient =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  public ExternalSigner(
      final URL signingServiceUrl, final BLSPublicKey blsPublicKey, final Duration timeout) {
    this.signingServiceUrl = signingServiceUrl;
    this.blsPublicKey = blsPublicKey;
    this.timeout = timeout;
  }

  @Override
  public SafeFuture<BLSSignature> createRandaoReveal(final UInt64 epoch, final ForkInfo forkInfo) {
    return sign(
        signingRootForRandaoReveal(epoch, forkInfo),
        SignType.RANDAO_REVEAL,
        Map.of("randao_reveal", Map.of("epoch", epoch), FORK_INFO, forkInfo(forkInfo)));
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(final BeaconBlock block, final ForkInfo forkInfo) {
    return sign(
        signingRootForSignBlock(block, forkInfo),
        SignType.BLOCK,
        Map.of(
            "block",
            new tech.pegasys.teku.api.schema.BeaconBlock(block),
            FORK_INFO,
            forkInfo(forkInfo)));
  }

  @Override
  public SafeFuture<BLSSignature> signAttestationData(
      final AttestationData attestationData, final ForkInfo forkInfo) {
    return sign(
        signingRootForSignAttestationData(attestationData, forkInfo),
        SignType.ATTESTATION,
        Map.of(
            "attestation",
            new tech.pegasys.teku.api.schema.AttestationData(attestationData),
            FORK_INFO,
            forkInfo(forkInfo)));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(final UInt64 slot, final ForkInfo forkInfo) {
    return sign(
        signingRootForSignAggregationSlot(slot, forkInfo),
        SignType.AGGREGATION_SLOT,
        Map.of("aggregation_slot", Map.of("slot", slot), FORK_INFO, forkInfo(forkInfo)));
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final AggregateAndProof aggregateAndProof, final ForkInfo forkInfo) {
    return sign(
        signingRootForSignAggregateAndProof(aggregateAndProof, forkInfo),
        SignType.AGGREGATE_AND_PROOF,
        Map.of(
            "aggregate_and_proof",
            new tech.pegasys.teku.api.schema.AggregateAndProof(aggregateAndProof),
            FORK_INFO,
            forkInfo(forkInfo)));
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final VoluntaryExit voluntaryExit, final ForkInfo forkInfo) {
    return sign(
        signingRootForSignVoluntaryExit(voluntaryExit, forkInfo),
        SignType.VOLUNTARY_EXIT,
        Map.of(
            "voluntary_exit",
            new tech.pegasys.teku.api.schema.VoluntaryExit(voluntaryExit),
            FORK_INFO,
            forkInfo(forkInfo)));
  }

  @Override
  public boolean isLocal() {
    return false;
  }

  private Map<String, Object> forkInfo(final ForkInfo forkInfo) {
    return Map.of(
        "fork",
        new Fork(forkInfo.getFork()),
        "genesis_validators_root",
        forkInfo.getGenesisValidatorsRoot());
  }

  private SafeFuture<BLSSignature> sign(
      final Bytes signingRoot, final SignType type, final Map<String, Object> metadata) {
    final String publicKey = blsPublicKey.toBytesCompressed().toString();
    return SafeFuture.ofComposed(
        () -> {
          final String requestBody = createSigningRequestBody(signingRoot, type, metadata);
          final URI uri =
              signingServiceUrl.toURI().resolve(EXTERNAL_SIGNER_ENDPOINT + "/" + publicKey);
          final HttpRequest request =
              HttpRequest.newBuilder()
                  .uri(uri)
                  .timeout(timeout)
                  .header("Content-Type", "application/json")
                  .POST(BodyPublishers.ofString(requestBody))
                  .build();
          return httpClient
              .sendAsync(request, BodyHandlers.ofString())
              .handleAsync(this::getBlsSignature);
        });
  }

  private String createSigningRequestBody(
      final Bytes signingRoot, final SignType type, final Map<String, Object> metadata) {
    try {
      return jsonProvider.objectToJSON(new SigningRequestBody(signingRoot, type, metadata));
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
      final String returnedContentType = response.headers().firstValue("Content-Type").orElse("");
      final String signatureHexStr =
          returnedContentType.startsWith("application/json")
              ? jsonProvider.jsonToObject(response.body(), SigningResponseBody.class).getSignature()
              : response.body();

      final Bytes signature = Bytes.fromHexString(signatureHexStr);
      return BLSSignature.fromBytesCompressed(signature);
    } catch (final IllegalArgumentException | JsonProcessingException e) {
      throw new ExternalSignerException(
          "External signer returned an invalid signature: " + e.getMessage(), e);
    }
  }
}
