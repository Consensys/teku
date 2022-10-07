/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.createForkInfo;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;

@ExtendWith(MockServerExtension.class)
public class ExternalSignerBellatrixBlockSigningIntegrationTest {
  private static final Duration TIMEOUT = Duration.ofMillis(500);
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private static final BLSKeyPair KEYPAIR = BLSTestUtil.randomKeyPair(1234);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final ThrottlingTaskQueueWithPriority queue =
      ThrottlingTaskQueueWithPriority.create(
          8, metricsSystem, TekuMetricCategory.VALIDATOR, "externalSignerTest");

  private ClientAndServer client;
  private ExternalSigner externalSigner;

  @BeforeEach
  void setup(final ClientAndServer client) throws MalformedURLException {
    this.client = client;
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerPublicKeySources(List.of(KEYPAIR.getPublicKey().toString()))
            .validatorExternalSignerUrl(new URL("http://127.0.0.1:" + client.getLocalPort()))
            .validatorExternalSignerTimeout(TIMEOUT)
            .build();
    final HttpClientExternalSignerFactory httpClientExternalSignerFactory =
        new HttpClientExternalSignerFactory(config);

    externalSigner =
        new ExternalSigner(
            spec,
            httpClientExternalSignerFactory.get(),
            config.getValidatorExternalSignerUrl(),
            KEYPAIR.getPublicKey(),
            TIMEOUT,
            queue,
            metricsSystem);
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  @Test
  void shouldSignBellatrixBlock() throws Exception {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlock(block, fork).join();
    assertThat(response).isEqualTo(expectedSignature);

    final ExternalSignerBlockRequestProvider externalSignerBlockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);

    final Bytes blockHeaderSigningRoot =
        signingRootUtil.signingRootForSignBlockHeader(BeaconBlockHeader.fromBlock(block), fork);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            blockHeaderSigningRoot,
            externalSignerBlockRequestProvider.getSignType(),
            externalSignerBlockRequestProvider.getBlockMetadata(
                Map.of("fork_info", createForkInfo(fork))));

    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);

    validateMetrics(metricsSystem, 1, 0, 0);
  }
}
