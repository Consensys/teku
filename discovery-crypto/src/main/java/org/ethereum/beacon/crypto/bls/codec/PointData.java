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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.Arrays;
import org.ethereum.beacon.crypto.bls.milagro.MilagroCodecs;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * An interface and implementations of intermediate format for <code>G<sub>1</sub></code> and <code>
 * G<sub>2</sub></code> points.
 *
 * <p>Such an intermediary has been introduced to make point format abstract from implementation of
 * elliptic curve math that uses these points on a higher layer of abstraction.
 *
 * <p>Point representation that is defined within this interface is encoded/decoded by codecs
 * defined in {@link Codec}. To make an end-to-math codecs it's assumed that yet another layer of
 * codecs to be introduced that is based on {@link Codec} implementations and stick with a certain
 * elliptic curve math. Check {@link MilagroCodecs} as an example.
 *
 * <p>Use {@link Validator} interface and its implementations to check the validity of arbitrary
 * byte sequence against the format described by the spec.
 *
 * @param <ENCODED> a type that represents encoded point data.
 * @see Codec
 * @see Validator
 * @see Flags
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#point-representations">Format
 *     specification</a>
 */
public interface PointData<ENCODED extends BytesValue> {

  /**
   * Should check whether point is the point at infinity.
   *
   * @return {@code true} if point is the point at infinity, {@code false} otherwise.
   */
  boolean isInfinity();

  /**
   * Should return a value of sign flag.
   *
   * @return {@code 1} if sign flag is set, {@code 0} if it's unset.
   */
  int getSign();

  /**
   * Should return a byte sequence of encoded point.
   *
   * @return encoded point.
   */
  ENCODED encode();

  /**
   * Represents a point belonging to <code>G<sub>1</sub></code> subgroup.
   *
   * <p>Point is encoded as its {@code x} coordinate with highest bits set to {@link Flags} bits.
   *
   * @see Flags
   * @see Validator#G1
   * @see Codec#G1
   */
  class G1 implements PointData<Bytes48> {
    private final Flags flags;
    private final byte[] x;

    G1(Flags flags, byte[] x) {
      this.flags = flags;
      this.x = x;
    }

    public static G1 create(byte[] x, boolean infinity, int sign) {
      return new G1(Flags.create(infinity, sign), x);
    }

    public byte[] getX() {
      return x;
    }

    public Flags getFlags() {
      return flags;
    }

    byte[] writeFlags(byte[] stream) {
      byte[] withFlags = stream.clone();
      withFlags[0] = flags.write(stream[0]);
      return withFlags;
    }

    @Override
    public boolean isInfinity() {
      return flags.test(Flags.INFINITY) > 0;
    }

    @Override
    public int getSign() {
      return flags.test(Flags.SIGN);
    }

    @Override
    public Bytes48 encode() {
      return Codec.G1.encode(this);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      G1 g1 = (G1) o;
      return Objects.equal(flags, g1.flags) && Arrays.equals(x, g1.x);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("flags", flags)
          .add("x", BytesValue.wrap(x))
          .toString();
    }
  }

  /**
   * Represents a point belonging to <code>G<sub>2</sub></code> subgroup.
   *
   * <p>Point is encoded as its {@code x1} coordinate with highest bits set to {@link Flags} bits,
   * followed by {@code x2} coordinate with highest three bits set to {@code 0}.
   *
   * @see Flags
   * @see Validator#G2
   * @see Codec#G2
   */
  class G2 implements PointData<Bytes96> {
    private final Flags flags1;
    private final Flags flags2;
    private final byte[] x1;
    private final byte[] x2;

    G2(Flags flags1, Flags flags2, byte[] x1, byte[] x2) {
      this.flags1 = flags1;
      this.flags2 = flags2;
      this.x1 = x1;
      this.x2 = x2;
    }

    public static G2 create(byte[] x1, byte[] x2, boolean infinity, int sign) {
      return new G2(Flags.create(infinity, sign), Flags.empty(), x1, x2);
    }

    public byte[] getX1() {
      return x1;
    }

    public byte[] getX2() {
      return x2;
    }

    public Flags getFlags1() {
      return flags1;
    }

    public Flags getFlags2() {
      return flags2;
    }

    byte[] writeFlags(byte[] stream) {
      byte[] withFlags = stream.clone();
      withFlags[0] = flags1.write(stream[0]);
      return withFlags;
    }

    @Override
    public boolean isInfinity() {
      return flags1.test(Flags.INFINITY) > 0;
    }

    @Override
    public int getSign() {
      return flags1.test(Flags.SIGN);
    }

    @Override
    public Bytes96 encode() {
      return Codec.G2.encode(this);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      G2 data = (G2) o;
      return Objects.equal(flags1, data.flags1)
          && Objects.equal(flags2, data.flags2)
          && Arrays.equals(x1, data.x1)
          && Arrays.equals(x2, data.x2);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("flags1", flags1)
          .add("flags2", flags2)
          .add("x1", BytesValue.wrap(x1))
          .add("x2", BytesValue.wrap(x2))
          .toString();
    }
  }
}
