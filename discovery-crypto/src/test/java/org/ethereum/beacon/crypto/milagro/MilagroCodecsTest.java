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

package org.ethereum.beacon.crypto.milagro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.crypto.bls.milagro.MilagroCodecs.G1;
import static org.ethereum.beacon.crypto.bls.milagro.MilagroCodecs.G2;

import java.math.BigInteger;
import java.util.Random;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS48.ROM;
import org.apache.milagro.amcl.RAND;
import org.ethereum.beacon.crypto.bls.codec.Flags;
import org.ethereum.beacon.crypto.bls.milagro.BIGs;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class MilagroCodecsTest {

  private static final BigInteger G1_SCALAR_FOR_SET_SIGN =
      new BigInteger(
          "1229748c56a39c664764dd7ef89f0b65fad5d836973a42d10157c6b071b0a843bea30c894da9bc72d35ce49c537f45d0b41df",
          16);

  private static final BigInteger G1_SCALAR_FOR_UNSET_SIGN =
      new BigInteger(
          "11156991f99cbfaf858bd219b0043fbd8927d352dbcb24e61de68fba1362fbba936fbd0ec3a06fc7dda6e32fd080f6e438981",
          16);

  private static final BigInteger G2_SCALAR_FOR_SET_SIGN =
      new BigInteger(
          "1229748c56a39c664764dd7ef89f0b65fad5d836973a42d10157c6b071b0a843bea30c894da9bc72d35ce49c537f45d0b41df",
          16);

  private static final BigInteger G2_SCALAR_FOR_UNSET_SIGN =
      new BigInteger(
          "628796e7027d0b2b1fc6b544d731949a27a697dc261fc5d3c0270fd638bf39e7293a8943b681ea6dd792301f595b27caf419",
          16);

  @Test
  public void encodeSignInG1() {
    ECP withSign = ECP.generator().mul(BIGs.fromBigInteger(G1_SCALAR_FOR_SET_SIGN));
    BytesValue encoded = G1.encode(withSign);
    Flags flags = Flags.read(encoded.getArrayUnsafe()[0]);

    assertThat(flags.isSignSet()).isTrue();

    ECP withoutSign = ECP.generator().mul(BIGs.fromBigInteger(G1_SCALAR_FOR_UNSET_SIGN));
    encoded = G1.encode(withoutSign);
    flags = Flags.read(encoded.getArrayUnsafe()[0]);

    assertThat(flags.isSignSet()).isFalse();
  }

  @Test
  public void encodeSignInG2() {
    ECP2 withSign = ECP2.generator().mul(BIGs.fromBigInteger(G2_SCALAR_FOR_SET_SIGN));
    BytesValue encoded = G2.encode(withSign);
    Flags flags = Flags.read(encoded.getArrayUnsafe()[0]);

    assertThat(flags.isSignSet()).isTrue();

    ECP2 withoutSign = ECP2.generator().mul(BIGs.fromBigInteger(G2_SCALAR_FOR_UNSET_SIGN));
    encoded = G2.encode(withoutSign);
    flags = Flags.read(encoded.get(0));

    assertThat(flags.isSignSet()).isFalse();
  }

  @Test
  public void encodeRandomPointInG1() {
    ECP point = ECP.generator().mul(randomBIG());
    Bytes48 encoded = G1.encode(point);
    ECP decoded = G1.decode(encoded);

    assert BIG.comp(decoded.getX(), point.getX()) == 0;
    assert BIG.comp(decoded.getY(), point.getY()) == 0;
  }

  @Test
  public void encodeInfinityInG1() {
    ECP infinity = new ECP();
    Bytes48 encoded = G1.encode(infinity);
    ECP decoded = G1.decode(encoded);

    assertThat(decoded.is_infinity()).isTrue();
  }

  @Test
  public void encodeRandomPointInG2() {
    ECP2 point = ECP2.generator().mul(randomBIG());
    Bytes96 encoded = G2.encode(point);
    ECP2 decoded = G2.decode(encoded);

    assert BIG.comp(decoded.getX().getA(), point.getX().getA()) == 0;
    assert BIG.comp(decoded.getX().getB(), point.getX().getB()) == 0;

    assert BIG.comp(decoded.getY().getA(), point.getY().getA()) == 0;
    assert BIG.comp(decoded.getY().getB(), point.getY().getB()) == 0;
  }

  @Test
  public void encodeInfinityInG2() {
    ECP2 point = new ECP2();
    Bytes96 encoded = G2.encode(point);
    ECP2 decoded = G2.decode(encoded);

    assertThat(decoded.is_infinity()).isTrue();
  }

  BIG randomBIG() {
    Random random = new Random();
    byte[] seed = new byte[32];
    random.nextBytes(seed);
    RAND rand = new RAND();
    rand.seed(seed.length, seed);

    return BIG.randomnum(new BIG(ROM.Modulus), rand);
  }
}
