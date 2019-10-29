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

package org.ethereum.beacon.crypto.bls.milagro;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.FP2;
import org.ethereum.beacon.crypto.bls.codec.Codec;
import org.ethereum.beacon.crypto.bls.codec.PointData;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;

/**
 * Codec implementations that work with Milagro point implementation.
 *
 * <p>Built on top of {@link Codec}. Encode and decode {@link ECP} and {@link ECP2} types to/from
 * byte sequence using point representation described in the spec.
 *
 * @see Codec
 * @see ECP
 * @see ECP2
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#point-representations">https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#point-representations</a>
 */
public abstract class MilagroCodecs {
  private MilagroCodecs() {}

  /**
   * Encodes/decodes byte sequence to/from {@link ECP}.
   *
   * @see Codec#G1
   */
  public static final Codec<ECP, Bytes48> G1 =
      new Codec<ECP, Bytes48>() {
        @Override
        public ECP decode(Bytes48 encoded) {
          PointData.G1 g1 = Codec.G1.decode(encoded);
          if (g1.isInfinity()) {
            return new ECP();
          } else {
            BIG x = BIGs.fromByteArray(g1.getX());
            BIG y = ECP.RHS(new FP(x)).sqrt().redc();

            if (FPs.getSign(y) == g1.getSign()) {
              return new ECP(x, y);
            } else {
              return new ECP(x, FPs.neg(y));
            }
          }
        }

        @Override
        public Bytes48 encode(ECP point) {
          byte[] x = BIGs.toByteArray(point.getX());
          int sign = FPs.getSign(point.getY());
          PointData.G1 data = PointData.G1.create(x, point.is_infinity(), sign);
          return data.encode();
        }
      };

  /**
   * Encodes/decodes byte sequence to/from {@link ECP2}.
   *
   * @see Codec#G2
   */
  public static final Codec<ECP2, Bytes96> G2 =
      new Codec<ECP2, Bytes96>() {
        @Override
        public ECP2 decode(Bytes96 encoded) {
          PointData.G2 g2 = Codec.G2.decode(encoded);
          if (g2.isInfinity()) {
            return new ECP2();
          } else {
            BIG im = BIGs.fromByteArray(g2.getX1());
            BIG re = BIGs.fromByteArray(g2.getX2());

            FP2 x = new FP2(re, im);
            FP2 y = ECP2.RHS(x);
            y.sqrt();

            if (FPs.getSign(y) == g2.getSign()) {
              return new ECP2(x, y);
            } else {
              y.neg();
              return new ECP2(x, y);
            }
          }
        }

        @Override
        public Bytes96 encode(ECP2 point) {
          byte[] re = BIGs.toByteArray(point.getX().getA());
          byte[] im = BIGs.toByteArray(point.getX().getB());
          int sign = FPs.getSign(point.getY());
          PointData.G2 data = PointData.G2.create(im, re, point.is_infinity(), sign);
          return data.encode();
        }
      };
}
