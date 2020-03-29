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

package tech.pegasys.artemis.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSSignature;

class SignerTest {
  private final MessageSignerService signerService = mock(MessageSignerService.class);

  private final Signer signer = new Signer(signerService);

  @Test
  public void shouldCreateRandaoReveal() {
    final BLSSignature signature = BLSSignature.empty();
    final Bytes expectedSigningRoot =
        Bytes.fromHexString("0x4c0e022c60384ac0268588a8e114968d2df4dddccb93e8e858b8c2a57d8f7338");
    when(signerService.signRandaoReveal(expectedSigningRoot))
        .thenReturn(SafeFuture.completedFuture(signature));
    final SafeFuture<BLSSignature> reveal =
        signer.createRandaoReveal(
            UnsignedLong.valueOf(7),
            new Fork(
                Bytes4.fromHexString("0x11111111"),
                Bytes4.fromHexString("0x22222222"),
                UnsignedLong.valueOf(6)));

    verify(signerService).signRandaoReveal(expectedSigningRoot);
    assertThat(reveal).isCompletedWithValue(signature);
  }
}
