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
import static tech.pegasys.teku.api.schema.deneb.BlindedBlobSidecar.fromInternalBlindedBlobSidecar;
import static tech.pegasys.teku.api.schema.deneb.BlindedBlobSidecar.fromInternalBlobSidecar;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.createForkInfo;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class ExternalSignerDenebIntegrationTest extends AbstractExternalSignerIntegrationTest {

  @Override
  public Spec getSpec() {
    return TestSpecFactory.createMinimalDeneb();
  }

  @Test
  void shouldSignBlobSidecar() throws Exception {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBlobSidecar(blobSidecar, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final Bytes signingRoot = signingRootUtil.signingRootForBlobSidecar(blobSidecar, forkInfo);
    final SignType signtype = SignType.BLOB_SIDECAR;
    final Map<String, Object> metadata =
        Map.of(
            "fork_info",
            createForkInfo(forkInfo),
            "blob_sidecar",
            fromInternalBlobSidecar(blobSidecar));
    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(signingRoot, signtype, metadata);
    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);
    validateMetrics(metricsSystem, 1, 0, 0);
  }

  @Test
  void shouldSignBlindedBlobSidecar() throws Exception {
    final BlindedBlobSidecar blindedBlobSidecar = dataStructureUtil.randomBlindedBlobSidecar();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signBlindedBlobSidecar(blindedBlobSidecar, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final Bytes signingRoot =
        signingRootUtil.signingRootForBlindedBlobSidecar(blindedBlobSidecar, forkInfo);
    final SignType signtype = SignType.BLOB_SIDECAR;
    final Map<String, Object> metadata =
        Map.of(
            "fork_info",
            createForkInfo(forkInfo),
            "blob_sidecar",
            fromInternalBlindedBlobSidecar(blindedBlobSidecar));
    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(signingRoot, signtype, metadata);
    verifySignRequest(client, KEYPAIR.getPublicKey().toString(), signingRequestBody);
    validateMetrics(metricsSystem, 1, 0, 0);
  }
}
