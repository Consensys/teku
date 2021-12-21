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

package tech.pegasys.teku.bls.impl.blst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.AbstractBLS12381Test;
import tech.pegasys.teku.bls.impl.BLS12381;

public class BlstTest extends AbstractBLS12381Test {
  private static final Random random = new Random(1);

  private static BLS12381 BLS;

  static BlstSignature notInG2() {
    // A point on the curve but not in the G2 group
    return BlstSignature.fromBytes(
        Bytes.fromHexString(
            "0x"
                + "8000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000004"));
  }

  @BeforeAll
  static void setup() {
    BLS = BlstLoader.INSTANCE.orElseThrow();
  }

  @Override
  protected BLS12381 getBls() {
    return BLS;
  }

  @Test
  void testBatchVerifySingleSig() {
    Bytes msg = Bytes32.ZERO;

    BlstSecretKey blstSK = BlstSecretKey.generateNew(random);
    BlstPublicKey blstPK = blstSK.derivePublicKey();

    BlstSignature blstSignature = BlstBLS12381.sign(blstSK, msg);

    BatchSemiAggregate semiAggregate =
        BLS.prepareBatchVerify(0, List.of(blstPK), msg, blstSignature);

    boolean blstRes = BLS.completeBatchVerify(List.of(semiAggregate));
    assertThat(blstRes).isTrue();
  }

  @Test
  void testBatchVerifyCoupleSigs() {
    Bytes msg1 = Bytes32.fromHexString("123456");

    BlstSecretKey blstSK1 = BlstSecretKey.generateNew(random);
    BlstPublicKey blstPK1 = blstSK1.derivePublicKey();
    BlstSignature blstSignature1 = BlstBLS12381.sign(blstSK1, msg1);

    Bytes msg2 = Bytes32.fromHexString("654321");

    BlstSecretKey blstSK2 = BlstSecretKey.generateNew(random);
    BlstPublicKey blstPK2 = blstSK2.derivePublicKey();
    BlstSignature blstSignature2 = BlstBLS12381.sign(blstSK2, msg2);

    BatchSemiAggregate semiAggregate1 =
        BLS.prepareBatchVerify(0, List.of(blstPK1), msg1, blstSignature1);
    BatchSemiAggregate semiAggregate2 =
        BLS.prepareBatchVerify(1, List.of(blstPK2), msg2, blstSignature2);

    boolean blstRes = BLS.completeBatchVerify(List.of(semiAggregate1, semiAggregate2));
    assertThat(blstRes).isTrue();
  }

  @Test
  void succeedsWhenPrepareBatchVerifyNotInG2ThrowsException() {
    Bytes msg = Bytes32.fromHexString("123456");
    BlstSecretKey blstSK = BlstSecretKey.generateNew(random);
    BlstPublicKey blstPK = blstSK.derivePublicKey();
    BlstSignature blstSignature = notInG2();

    assertThrows(
        IllegalArgumentException.class,
        () -> BLS.prepareBatchVerify(0, List.of(blstPK), msg, blstSignature));
  }
}
