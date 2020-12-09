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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.matchers.MatchType.STRICT;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForRandaoReveal;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAggregateAndProof;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAggregationSlot;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignAttestationData;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignBlock;
import static tech.pegasys.teku.core.signatures.SigningRootUtil.signingRootForSignVoluntaryExit;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PRECONDITION_FAILED;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.slashableAttestationMessage;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.slashableBlockMessage;
import static tech.pegasys.teku.validator.client.signer.ExternalSigner.slashableGenericMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.Delay;
import org.mockserver.model.MediaType;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;

@ExtendWith(MockServerExtension.class)
public class ExternalSignerIntegrationTest {
  private static final Duration TIMEOUT = Duration.ofMillis(500);
  private static final BLSKeyPair KEYPAIR = BLSKeyPair.random(1234);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final ThrottlingTaskQueue queue =
      new ThrottlingTaskQueue(8, metricsSystem, TekuMetricCategory.VALIDATOR, "externalSignerTest");

  private ClientAndServer client;
  private ExternalSigner externalSigner;

  @BeforeEach
  void setup(final ClientAndServer client) throws MalformedURLException {
    this.client = client;
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerPublicKeys(List.of(KEYPAIR.getPublicKey()))
            .validatorExternalSignerUrl(new URL("http://127.0.0.1:" + client.getLocalPort()))
            .validatorExternalSignerTimeout(TIMEOUT)
            .build();
    final HttpClientExternalSignerFactory httpClientExternalSignerFactory =
        new HttpClientExternalSignerFactory(config);

    externalSigner =
        new ExternalSigner(
            httpClientExternalSignerFactory.get(),
            config.getValidatorExternalSignerUrl(),
            config.getValidatorExternalSignerPublicKeys().get(0),
            TIMEOUT,
            queue);
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  @Test
  void failsSigningWhenSigningServiceReturnsFailureResponse() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);

    assertThatThrownBy(() -> externalSigner.signBlock(block, fork).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(
            "External signer failed to sign and returned invalid response status code: 404");
  }

  @Test
  void failsSigningWhenSigningServiceTimesOut() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final long ensureTimeout = 5;
    final Delay delay = new Delay(MILLISECONDS, TIMEOUT.plusMillis(ensureTimeout).toMillis());
    client.when(request()).respond(response().withDelay(delay));

    assertThatThrownBy(() -> externalSigner.signBlock(block, fork).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(
            "External signer failed to sign due to java.net.http.HttpTimeoutException: request timed out");
  }

  @Test
  void failsSigningWhenSigningServiceReturnsInvalidSignatureResponse() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    client.when(request()).respond(response().withBody("INVALID_RESPONSE"));

    assertThatThrownBy(() -> externalSigner.signBlock(block, fork).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(
            "External signer returned an invalid signature: Illegal character 'I' found at index 0 in hex binary representation");
  }

  @Test
  void failsSigningBlockWhenSigningServiceRefusesToSignDueToSlashingCondition() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    client.when(request()).respond(response().withStatusCode(SC_PRECONDITION_FAILED));

    assertThatThrownBy(() -> externalSigner.signBlock(block, fork).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(slashableBlockMessage(block).get());
  }

  @Test
  void failsSigningAttestationDataWhenSigningServiceRefusesToSignDueToSlashingCondition() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    client.when(request()).respond(response().withStatusCode(SC_PRECONDITION_FAILED));

    assertThatThrownBy(() -> externalSigner.signAttestationData(attestationData, fork).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(slashableAttestationMessage(attestationData).get());
  }

  @Test
  void failsSigningRandaoRevealWhenSigningServiceRefusesToSignDueToSlashingCondition() {
    final UInt64 epoch = UInt64.valueOf(7);
    client.when(request()).respond(response().withStatusCode(SC_PRECONDITION_FAILED));

    assertThatThrownBy(() -> externalSigner.createRandaoReveal(epoch, fork).join())
        .hasCauseInstanceOf(ExternalSignerException.class)
        .hasMessageEndingWith(slashableGenericMessage("randao reveal").get());
  }

  @Test
  void shouldSignsBlock() throws Exception {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlock(block, fork).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootForSignBlock(block, fork),
            SignType.BLOCK,
            Map.of(
                "fork_info",
                createForkInfo(),
                "block",
                new tech.pegasys.teku.api.schema.BeaconBlock(block)));

    verifySignRequest(KEYPAIR.getPublicKey().toString(), signingRequestBody);
  }

  @Test
  void shouldSignAttestationData() throws Exception {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "l1DUv3fmbvZanhCaaraMk2PKAl+33sf3UHMbxkv18CKILzzIz+Hr6hnLXCHqWQYEGKTtLcf6OLV7Z+Y21BW2bBtJHXJqqzvWkec/j0X0hWaEoWOSAs20sipO1WSIUY2m"));

    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signAttestationData(attestationData, fork).join();
    assertThat(response).isEqualTo(expectedSignature);
    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootForSignAttestationData(attestationData, fork),
            SignType.ATTESTATION,
            Map.of(
                "fork_info",
                createForkInfo(),
                "attestation",
                new tech.pegasys.teku.api.schema.AttestationData(attestationData)));

    verifySignRequest(KEYPAIR.getPublicKey().toString(), signingRequestBody);
  }

  @Test
  void shouldSignRandaoReveal() throws Exception {
    final UInt64 epoch = UInt64.valueOf(7);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "j7vOT7GQBnv+aIqxb0byMWNvMCXhQwAfj38UcMne7pNGXOvNZKnXQ9Knma/NOPUyAvLcRBDtew23vVtzWcm7naaTRJVvLJS6xiPOMIHOw6wNtGggzc20heZAXZAMdaKi"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.createRandaoReveal(epoch, fork).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootForRandaoReveal(epoch, fork),
            SignType.RANDAO_REVEAL,
            Map.of("fork_info", createForkInfo(), "randao_reveal", Map.of("epoch", epoch)));
    verifySignRequest(KEYPAIR.getPublicKey().toString(), signingRequestBody);
  }

  @Test
  public void shouldSignAggregationSlot() throws Exception {
    final UInt64 slot = UInt64.valueOf(7);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "hnCLCZlbEyzMFq2JLHl6wk4W6gpbFGoQA2N4WB+CpgqVg3gcxJpRKOswtSTU4XdSEU2x3Hf0oTlxer/gVaFwAh84Mm4VLH67LNUxVO4+o2Q5TxOD1sArnvMcOJdGMGp2"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final SafeFuture<BLSSignature> future = externalSigner.signAggregationSlot(slot, fork);

    assertThat(future.get()).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootForSignAggregationSlot(slot, fork),
            SignType.AGGREGATION_SLOT,
            Map.of("fork_info", createForkInfo(), "aggregation_slot", Map.of("slot", slot)));
    verifySignRequest(KEYPAIR.getPublicKey().toString(), signingRequestBody);
  }

  @Test
  public void shouldSignAggregateAndProof() throws Exception {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "kHbIuvFcS/kDppbCj0ILOU27ZjSU1P2wPsOKBBwGaz1uvXQxtUXQAdbybN1zotZqCs6pstChIIxDS/WgAZH2z4yX2cM/cM/iKayT2rZZJuu31V2uxP1AYVcyHLEMtF07"));

    client.when(request()).respond(response().withBody(expectedSignature.toString()));
    final BLSSignature response =
        externalSigner.signAggregateAndProof(aggregateAndProof, fork).join();

    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootForSignAggregateAndProof(aggregateAndProof, fork),
            SignType.AGGREGATE_AND_PROOF,
            Map.of(
                "fork_info",
                createForkInfo(),
                "aggregate_and_proof",
                new tech.pegasys.teku.api.schema.AggregateAndProof(aggregateAndProof)));
    verifySignRequest(KEYPAIR.getPublicKey().toString(), signingRequestBody);
  }

  @Test
  public void shouldSignVoluntaryExit() throws Exception {
    final VoluntaryExit voluntaryExit = dataStructureUtil.randomVoluntaryExit();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "g9JMIY7595zlrapmwbnCLj8+WX7ry3yfBwNNPQ9mRJ0m+rXTwgDpmsxpzs+kX4F8Bg+KRz+v5BPKEAWkeh8bJBDX7psiELLI3q9WmCX95MXT080jByrtYLdz1Qy3OUKK"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));
    final BLSSignature response = externalSigner.signVoluntaryExit(voluntaryExit, fork).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootForSignVoluntaryExit(voluntaryExit, fork),
            SignType.VOLUNTARY_EXIT,
            Map.of(
                "fork_info",
                createForkInfo(),
                "voluntary_exit",
                new tech.pegasys.teku.api.schema.VoluntaryExit(voluntaryExit)));
    verifySignRequest(KEYPAIR.getPublicKey().toString(), signingRequestBody);
  }

  private Map<String, Object> createForkInfo() {
    return Map.of(
        "genesis_validators_root",
        fork.getGenesisValidatorsRoot(),
        "fork",
        new Fork(fork.getFork()));
  }

  private void verifySignRequest(
      final String publicKey, final SigningRequestBody signingRequestBody)
      throws JsonProcessingException {
    client.verify(
        request()
            .withMethod("POST")
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(json(jsonProvider.objectToJSON(signingRequestBody), STRICT))
            .withPath(ExternalSigner.EXTERNAL_SIGNER_ENDPOINT + "/" + publicKey));
  }
}
