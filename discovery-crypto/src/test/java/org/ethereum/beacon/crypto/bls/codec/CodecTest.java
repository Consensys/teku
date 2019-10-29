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

import java.math.BigInteger;
import java.util.Random;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.beacon.crypto.bls.bc.BCParameters;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;

public class CodecTest {

  @Test
  public void readWriteFlags() {
    byte[] stream = new byte[1];
    Flags origin = Flags.create(false, 1);

    byte[] flagged = stream.clone();
    flagged[0] = origin.write(stream[0]);
    assertThat(flagged[0]).isEqualTo((byte) 0xA0);

    Flags read = Flags.read(flagged[0]);
    assertThat(read).isEqualTo(origin);

    byte highestByte = Flags.erase(flagged[0]);
    assertThat(highestByte).isEqualTo((byte) 0x00);
  }

  @Test
  public void encodeInfinityInG1() {
    byte[] x = randomX();

    Bytes48 encoded = Codec.G1.encode(PointData.G1.create(x, true, 1));
    Flags flags = Flags.read(encoded.get(0));

    assertThat(flags.test(Flags.C)).isEqualTo(1);
    assertThat(flags.test(Flags.INFINITY)).isEqualTo(1);
    assertThat(flags.test(Flags.SIGN)).isEqualTo(0);

    byte[] withoutFlags = encoded.extractArray();
    withoutFlags[0] = Flags.erase(encoded.get(0));
    assertThat(withoutFlags).isEqualTo(new byte[BCParameters.Q_BYTE_LENGTH]);

    PointData.G1 decoded = Codec.G1.decode(encoded);
    assertThat(decoded.getFlags()).isEqualTo(flags);
    assertThat(decoded.getX()).isEqualTo(new byte[BCParameters.Q_BYTE_LENGTH]);
  }

  @Test
  public void encodeG1WithSign() {
    byte[] x = randomX();

    Bytes48 encoded = Codec.G1.encode(PointData.G1.create(x, false, 1));
    Flags flags = Flags.read(encoded.get(0));

    assertThat(flags.test(Flags.C)).isEqualTo(1);
    assertThat(flags.test(Flags.INFINITY)).isEqualTo(0);
    assertThat(flags.test(Flags.SIGN)).isEqualTo(1);

    byte[] withoutFlags = encoded.extractArray();
    withoutFlags[0] = Flags.erase(encoded.get(0));
    assertThat(withoutFlags).isEqualTo(x);

    PointData.G1 decoded = Codec.G1.decode(encoded);
    assertThat(decoded.getFlags()).isEqualTo(flags);
    assertThat(decoded.getX()).isEqualTo(x);
  }

  @Test
  public void encodeInfinityInG2() {
    byte[] x1 = randomX();
    byte[] x2 = randomX();

    Bytes96 encoded = Codec.G2.encode(PointData.G2.create(x1, x2, true, 1));
    Flags flags = Flags.read(encoded.get(0));

    assertThat(flags.test(Flags.C)).isEqualTo(1);
    assertThat(flags.test(Flags.INFINITY)).isEqualTo(1);
    assertThat(flags.test(Flags.SIGN)).isEqualTo(0);

    byte[] withoutFlags = encoded.extractArray();
    withoutFlags[0] = Flags.erase(encoded.get(0));
    assertThat(withoutFlags).isEqualTo(new byte[BCParameters.Q_BYTE_LENGTH * 2]);

    PointData.G2 decoded = Codec.G2.decode(encoded);
    assertThat(decoded.getFlags1()).isEqualTo(flags);
    assertThat(decoded.getFlags2().isZero()).isTrue();
    assertThat(decoded.getX1()).isEqualTo(new byte[BCParameters.Q_BYTE_LENGTH]);
    assertThat(decoded.getX2()).isEqualTo(new byte[BCParameters.Q_BYTE_LENGTH]);
  }

  @Test
  public void encodeG2WithSign() {
    byte[] x1 = randomX();
    byte[] x2 = randomX();

    Bytes96 encoded = Codec.G2.encode(PointData.G2.create(x1, x2, false, 1));
    Flags flags = Flags.read(encoded.get(0));

    assertThat(flags.test(Flags.C)).isEqualTo(1);
    assertThat(flags.test(Flags.INFINITY)).isEqualTo(0);
    assertThat(flags.test(Flags.SIGN)).isEqualTo(1);

    byte[] withoutFlags = encoded.extractArray();
    withoutFlags[0] = Flags.erase(encoded.get(0));
    assertThat(withoutFlags).isEqualTo(Arrays.concatenate(x1, x2));

    PointData.G2 decoded = Codec.G2.decode(encoded);
    assertThat(decoded.getFlags1()).isEqualTo(flags);
    assertThat(decoded.getFlags2().isZero()).isTrue();
    assertThat(decoded.getX1()).isEqualTo(x1);
    assertThat(decoded.getX2()).isEqualTo(x2);
  }

  @Test
  public void checkG1WithRandomData() {
    PointData.G1 data = randomDataG1();
    Bytes48 encoded = Codec.G1.encode(data);
    PointData.G1 decoded = Codec.G1.decode(encoded);

    assertThat(decoded).isEqualTo(data);
  }

  @Test
  public void checkG2WithRandomData() {
    PointData.G2 data = randomDataG2();
    Bytes96 encoded = Codec.G2.encode(data);
    PointData.G2 decoded = Codec.G2.decode(encoded);

    assertThat(decoded).isEqualTo(data);
  }

  PointData.G1 randomDataG1() {
    Random random = new Random();
    byte[] x = new byte[BCParameters.Q_BYTE_LENGTH];
    boolean infinity = random.nextBoolean();
    int sign = random.nextBoolean() ? 1 : 0;

    if (!infinity) {
      random.nextBytes(x);
      x[0] = Flags.erase(x[0]);
    }

    return PointData.G1.create(x, infinity, sign);
  }

  PointData.G2 randomDataG2() {
    Random random = new Random();
    byte[] x1 = new byte[BCParameters.Q_BYTE_LENGTH];
    byte[] x2 = new byte[BCParameters.Q_BYTE_LENGTH];
    boolean infinity = random.nextBoolean();
    int sign = random.nextBoolean() ? 1 : 0;

    if (!infinity) {
      random.nextBytes(x1);
      x1[0] = Flags.erase(x1[0]);

      random.nextBytes(x2);
      x2[0] = Flags.erase(x2[0]);
    }

    return PointData.G2.create(x1, x2, infinity, sign);
  }

  byte[] randomX() {
    return BigIntegers.asUnsignedByteArray(
        BCParameters.Q_BYTE_LENGTH, new BigInteger(381, new Random()).mod(BCParameters.Q));
  }
}
