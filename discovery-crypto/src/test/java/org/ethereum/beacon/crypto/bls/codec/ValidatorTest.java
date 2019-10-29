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

package org.ethereum.beacon.crypto.bls.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.crypto.bls.codec.Validator.G1;
import static org.ethereum.beacon.crypto.bls.codec.Validator.G2;

import java.math.BigInteger;
import java.util.Random;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.beacon.crypto.bls.bc.BCParameters;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.MutableBytesValue;

public class ValidatorTest {

  @Test
  public void passForValidG1() {
    byte[] x = randomX();
    BytesValue encoded = PointData.G1.create(x, false, 1).encode();
    assertThat(G1.validate(encoded).isValid()).isTrue();

    encoded = PointData.G1.create(x, true, 0).encode();
    assertThat(G1.validate(encoded).isValid()).isTrue();
  }

  @Test
  public void passForValidG2() {
    byte[] x1 = randomX();
    byte[] x2 = randomX();
    BytesValue encoded = PointData.G2.create(x1, x2, false, 1).encode();
    assertThat(G2.validate(encoded).isValid()).isTrue();

    encoded = PointData.G2.create(x1, x2, true, 1).encode();
    assertThat(G2.validate(encoded).isValid()).isTrue();
  }

  @Test
  public void handleUnexpectedLength() {
    BytesValue shortArray = BytesValue.wrap(new byte[BCParameters.Q_BYTE_LENGTH / 2]);

    assertThat(G1.validate(shortArray).isValid()).isFalse();
    assertThat(G2.validate(shortArray).isValid()).isFalse();
  }

  @Test
  public void failIfCoordinatesAreNotLessThanQ() {
    byte[] invalidX = BCParameters.Q.toByteArray();
    byte[] validX = randomX();

    BytesValue g1WithInvalidX = PointData.G1.create(invalidX, false, 0).encode();
    assertThat(G1.validate(g1WithInvalidX).isValid()).isFalse();

    BytesValue g2WithInvalidX1 = PointData.G2.create(invalidX, validX, false, 0).encode();
    assertThat(G2.validate(g2WithInvalidX1).isValid()).isFalse();

    BytesValue g2WithInvalidX2 = PointData.G2.create(validX, invalidX, false, 0).encode();
    assertThat(G2.validate(g2WithInvalidX2).isValid()).isFalse();
  }

  @Test
  public void failIfCFlagIsNotSet() {
    byte[] x1 = randomX();
    byte[] x2 = randomX();

    BytesValue g1Encoded = PointData.G1.create(x1, false, 0).encode();
    MutableBytesValue g1CFlagUnset = MutableBytesValue.wrap(g1Encoded.extractArray());
    g1CFlagUnset.set(0, (byte) (g1CFlagUnset.get(0) & 0x7F));

    assertThat(G1.validate(g1CFlagUnset).isValid()).isFalse();

    BytesValue g2Encoded = PointData.G2.create(x1, x2, false, 0).encode();
    MutableBytesValue g2CFlagUnset = MutableBytesValue.wrap(g2Encoded.extractArray());
    g2CFlagUnset.set(0, (byte) (g2CFlagUnset.get(0) & 0x7F));

    assertThat(G2.validate(g2CFlagUnset).isValid()).isFalse();
  }

  @Test
  public void failIfInfinityIsIncorrect() {
    byte[] zeros = new byte[BCParameters.Q_BYTE_LENGTH];
    byte[] x1 = randomX();
    byte[] x2 = randomX();

    MutableBytesValue g1InfBFlagSet =
        MutableBytesValue.wrap(PointData.G1.create(zeros, true, 0).encode().extractArray());
    g1InfBFlagSet.set(0, (byte) (g1InfBFlagSet.get(0) | 0x20));

    assertThat(G1.validate(g1InfBFlagSet).isValid()).isFalse();

    MutableBytesValue g1InfXNotZero =
        MutableBytesValue.wrap(PointData.G1.create(x1, false, 0).encode().extractArray());
    g1InfXNotZero.set(0, (byte) (g1InfXNotZero.get(0) | 0x40));

    assertThat(G1.validate(g1InfBFlagSet).isValid()).isFalse();

    MutableBytesValue g2InfBFlagSet =
        MutableBytesValue.wrap(PointData.G2.create(zeros, zeros, true, 0).encode().extractArray());
    g2InfBFlagSet.set(0, (byte) (g2InfBFlagSet.get(0) | 0x20));

    assertThat(G2.validate(g2InfBFlagSet).isValid()).isFalse();

    MutableBytesValue g2InfX1NotZero =
        MutableBytesValue.wrap(PointData.G2.create(x1, zeros, false, 0).encode().extractArray());
    g2InfX1NotZero.set(0, (byte) (g2InfX1NotZero.get(0) | 0x40));

    assertThat(G2.validate(g2InfX1NotZero).isValid()).isFalse();

    MutableBytesValue g2InfX2NotZero =
        MutableBytesValue.wrap(PointData.G2.create(zeros, x2, false, 0).encode().extractArray());
    g2InfX2NotZero.set(0, (byte) (g2InfX2NotZero.get(0) | 0x40));

    assertThat(G2.validate(g2InfX2NotZero).isValid()).isFalse();
  }

  @Test
  public void failIfG2WithNonEmptyFlag2() {
    byte[] x = randomX();

    BytesValue g1Encoded = PointData.G1.create(x, false, 1).encode();
    BytesValue g2WithFlag2 = g1Encoded.concat(g1Encoded);

    assertThat(G2.validate(g2WithFlag2).isValid()).isFalse();
  }

  byte[] randomX() {
    return BigIntegers.asUnsignedByteArray(
        BCParameters.Q_BYTE_LENGTH, new BigInteger(381, new Random()).mod(BCParameters.Q));
  }
}
