/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
public class BlobSidecarsByRootValidatorTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private BlobSidecarsByRootValidator validator;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);

  @BeforeEach
  void setUp() {
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(true);
  }

  @Test
  void blobSidecarIsCorrect() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final BlobIdentifier blobIdentifier1_0 = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 = dataStructureUtil.randomBlobSidecarForBlock(block1, 0);

    validator = new BlobSidecarsByRootValidator(peer, spec, kzg, List.of(blobIdentifier1_0));
    assertDoesNotThrow(() -> validator.validate(blobSidecar1_0));
  }

  @Test
  void blobSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final BlobIdentifier blobIdentifier2_0 =
        new BlobIdentifier(dataStructureUtil.randomBytes32(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 = dataStructureUtil.randomBlobSidecarForBlock(block1, 0);

    validator = new BlobSidecarsByRootValidator(peer, spec, kzg, List.of(blobIdentifier2_0));
    assertThatThrownBy(() -> validator.validate(blobSidecar1_0))
        .isExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @Test
  void blobSidecarFailsKzgVerification() {
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final BlobIdentifier blobIdentifier1_0 = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 = dataStructureUtil.randomBlobSidecarForBlock(block1, 0);

    validator = new BlobSidecarsByRootValidator(peer, spec, kzg, List.of(blobIdentifier1_0));
    assertThatThrownBy(() -> validator.validate(blobSidecar1_0))
        .isExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }
}
