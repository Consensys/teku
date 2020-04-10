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

package tech.pegasys.artemis.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.ForkInfo;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.util.async.SafeFuture;

class SignerTest {

  private final MessageSignerService signerService = mock(MessageSignerService.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();

  private final Signer signer = new Signer(signerService);

  @Test
  public void shouldCreateRandaoReveal() {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Bytes expectedSigningRoot =
        Bytes.fromHexString("0x84c8825ccb169a0d1109ff2b96185ea2bd6f66ed198f2e322473773fff3ed91b");
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
        Bytes.fromHexString("0xc1d77efb7248d3a1cac986ea27ce9ee8fb2257f8e6053ae0083b77d781af02d3");
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
        Bytes.fromHexString("0x08c353f48613183f31ebcb6d95bec46f9da73793cb0581f4a49d253224f778a1");
    when(signerService.signAttestation(expectedSigningRoot))
        .thenReturn(SafeFuture.completedFuture(signature));

    final SafeFuture<BLSSignature> result = signer.signAttestationData(attestationData, fork);

    verify(signerService).signAttestation(expectedSigningRoot);
    assertThat(result).isCompletedWithValue(signature);
  }
}
