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
import static tech.pegasys.teku.validator.client.signer.ExternalSignerIntegrationTestData.TIMEOUT;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.createForkInfo;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;

@ExtendWith(MockServerExtension.class)
public class ExternalSignerBlockSigningIntegrationTest {
  private static final BLSKeyPair KEYPAIR = BLSTestUtil.randomKeyPair(1234);

  private ClientAndServer client;
  private URL externalSignerUrl;

  @BeforeEach
  void setup(final ClientAndServer client) throws MalformedURLException {
    this.client = client;
    externalSignerUrl = new URL("http://127.0.0.1:" + client.getLocalPort());
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  @ParameterizedTest(name = "#{index} - Should sign Block for spec {0}")
  @EnumSource(
      value = SpecMilestone.class,
      names = {"BELLATRIX", "CAPELLA"})
  void shouldSignBlock(final SpecMilestone specMilestone) throws Exception {
    final Spec spec = TestSpecFactory.createMinimal(specMilestone);
    final ExternalSignerIntegrationTestData data = new ExternalSignerIntegrationTestData(spec);
    final ExternalSigner externalSigner =
        new ExternalSigner(
            spec,
            data.getHttpClient(externalSignerUrl, List.of(KEYPAIR.getPublicKey().toString())),
            externalSignerUrl,
            KEYPAIR.getPublicKey(),
            TIMEOUT,
            data.getQueue(),
            data.getMetricsSystem());
    final BeaconBlock block = data.getDataStructureUtil().randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlock(block, data.getFork()).join();
    assertThat(response).isEqualTo(expectedSignature);

    final ExternalSignerBlockRequestProvider externalSignerBlockRequestProvider =
        new ExternalSignerBlockRequestProvider(data.getSpec(), block);

    final Bytes blockHeaderSigningRoot =
        data.getSigningRootUtil()
            .signingRootForSignBlockHeader(BeaconBlockHeader.fromBlock(block), data.getFork());

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            blockHeaderSigningRoot,
            externalSignerBlockRequestProvider.getSignType(),
            externalSignerBlockRequestProvider.getBlockMetadata(
                Map.of("fork_info", createForkInfo(data.getFork()))));

    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);

    validateMetrics(data.getMetricsSystem(), 1, 0, 0);
  }
}
