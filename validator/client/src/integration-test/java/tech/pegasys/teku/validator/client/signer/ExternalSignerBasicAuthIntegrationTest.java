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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

public class ExternalSignerBasicAuthIntegrationTest extends AbstractExternalSignerIntegrationTest {

  private final String username = "foo";
  private final String password = "bar";

  @Override
  public Spec getSpec() {
    return TestSpecFactory.createMinimalAltair();
  }

  @Override
  protected URL getUrl() throws MalformedURLException {
    return new URL(
        String.format("http://%s:%s@127.0.0.1:%s", username, password, client.getLocalPort()));
  }

  @Test
  public void addsBasicAuthorizationToSigningRequest() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));

    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlock(block, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    // verify Authorization header
    final HttpRequest[] recordedRequests = client.retrieveRecordedRequests(request());
    assertThat(recordedRequests).hasSize(1);
    final String recordedAuthHeader = recordedRequests[0].getFirstHeader("Authorization");
    final String decodedAuthHeader =
        new String(
            Base64.getDecoder().decode(recordedAuthHeader.split(" ")[1]), StandardCharsets.UTF_8);
    assertThat(decodedAuthHeader).isEqualTo(username + ":" + password);
  }
}
