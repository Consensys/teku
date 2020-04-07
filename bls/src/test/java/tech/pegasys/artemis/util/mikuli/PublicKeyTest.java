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

package tech.pegasys.artemis.util.mikuli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.mikuli.PublicKey;
import tech.pegasys.artemis.bls.mikuli.Scalar;
import tech.pegasys.artemis.bls.mikuli.SecretKey;

public class PublicKeyTest {

  @Test
  void zeroSecretKeyGivesPointAtInfinity() {
    PublicKey pubKey = new PublicKey(new SecretKey(new Scalar(new BIG(0))));
    assertTrue(pubKey.g1Point().ecpPoint().is_infinity());
  }

  @Test
  void succeedsWhenPassingEmptyListToAggregatePublicKeysDoesNotThrowException() {
    assertDoesNotThrow(() -> PublicKey.aggregate(Collections.emptyList()));
  }

  @Test
  public void shouldHaveConsistentHashCodeAndEquals() {
    final PublicKey key =
        PublicKey.fromBytesCompressed(
            Bytes.fromHexString(
                "0x81283b7a20e1ca460ebd9bbd77005d557370cabb1f9a44f530c4c4c66230f675f8df8b4c2818851aa7d77a80ca5a4a5e"));
    final PublicKey same = PublicKey.fromBytesCompressed(key.toBytesCompressed());

    assertEquals(key, same);
    assertEquals(key.hashCode(), same.hashCode());
  }
}
