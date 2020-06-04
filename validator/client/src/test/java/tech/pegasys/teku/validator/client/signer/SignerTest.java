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

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.MessageSignerService;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.util.async.SafeFuture;

class SignerTest {

  private final MessageSignerService signerService = mock(MessageSignerService.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();

  private final Signer signer = new Signer(signerService);

  @Test
  public void shouldCreateRandaoReveal() {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Bytes expectedSigningRoot =
        Bytes.fromHexString("0xf133dca1e6b9f68ed8388345e1a3f833fcaed6567c44788cae15815c1b2f95d7");
    when(signerService.signRandaoReveal(expectedSigningRoot))
        .thenReturn(SafeFuture.completedFuture(signature));
    final SafeFuture<BLSSignature> reveal =
        signer.createRandaoReveal(UnsignedLong.valueOf(7), fork);

    verify(signerService).signRandaoReveal(expectedSigningRoot);
    assertThat(reveal).isCompletedWithValue(signature);
  }

  @Test
  public void shouldSignBlock() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Bytes expectedSigningRoot =
    Bytes.fromHexString("0x4f8543ecbb40d4f3b492fb1a890b9a569abc88b9cb1c3eac9f7c824623a3879d");
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
        Bytes.fromHexString("0xc9e1788b5b1864e701e69969418d635ac48f1e7b6ab65113f981798d55f305cc");
    when(signerService.signAttestation(expectedSigningRoot))
        .thenReturn(SafeFuture.completedFuture(signature));

    final SafeFuture<BLSSignature> result = signer.signAttestationData(attestationData, fork);

    verify(signerService).signAttestation(expectedSigningRoot);
    assertThat(result).isCompletedWithValue(signature);
  }
}
