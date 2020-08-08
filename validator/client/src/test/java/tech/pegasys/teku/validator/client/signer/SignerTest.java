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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.MessageSignerService;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.UnprotectedSigner;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SignerTest {

  private final MessageSignerService signerService = mock(MessageSignerService.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();

  private final Signer signer = new UnprotectedSigner(signerService);

  @Test
  public void shouldCreateRandaoReveal() {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Bytes expectedSigningRoot =
        Bytes.fromHexString("0xf133dca1e6b9f68ed8388345e1a3f833fcaed6567c44788cae15815c1b2f95d7");
    when(signerService.signRandaoReveal(expectedSigningRoot))
        .thenReturn(SafeFuture.completedFuture(signature));
    final SafeFuture<BLSSignature> reveal = signer.createRandaoReveal(UInt64.valueOf(7), fork);

    verify(signerService).signRandaoReveal(expectedSigningRoot);
    assertThat(reveal).isCompletedWithValue(signature);
  }

  @Test
  public void shouldSignBlock1() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Bytes expectedSigningRoot =
        Bytes.fromHexString("0xf6c68e87f3dbbe3d05de8eae204396ddea7fe13ffad0008f06748c9aa23fcc05");
    when(signerService.signBlock(expectedSigningRoot))
        .thenReturn(SafeFuture.completedFuture(signature));

    final SafeFuture<BLSSignature> result = signer.signBlock(block, fork);

    verify(signerService).signBlock(expectedSigningRoot);
    assertThat(result).isCompletedWithValue(signature);
  }

  @Test
  public void shouldSignAttestationData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Bytes expectedSigningRoot =
        Bytes.fromHexString("0x418f2ed4e878074a64b23102a90582d32099f3e74787a04a13ec0cecc86c070a");
    when(signerService.signAttestation(expectedSigningRoot))
        .thenReturn(SafeFuture.completedFuture(signature));

    final SafeFuture<BLSSignature> result = signer.signAttestationData(attestationData, fork);

    verify(signerService).signAttestation(expectedSigningRoot);
    assertThat(result).isCompletedWithValue(signature);
  }
}
