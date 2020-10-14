/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.mikuli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.BLS12381Test;

class MikuliBLS12381Test extends BLS12381Test {

  @Override
  protected BLS12381 getBls() {
    return MikuliBLS12381.INSTANCE;
  }

  @Test
  void signingWithZeroSecretKeyThrows() {
    MikuliSecretKey secretKey = new MikuliSecretKey(new Scalar(new BIG(0)));
    assertThrows(
        IllegalArgumentException.class,
        () -> secretKey.sign(Bytes.wrap("Hello, world!".getBytes(UTF_8))));
  }

  @Test
  void verifyingWithPointsAtInfinityFails() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    MikuliPublicKey infPubKey = new MikuliPublicKey(new G1Point());
    MikuliSignature infSignature = new MikuliSignature(new G2Point());

    assertFalse(infSignature.verify(infPubKey, message));
  }
}
