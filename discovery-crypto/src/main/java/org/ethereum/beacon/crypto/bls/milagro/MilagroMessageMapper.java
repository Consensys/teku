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

import static org.apache.milagro.amcl.BLS381.ECP2.RHS;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.crypto.MessageParameters;
import org.ethereum.beacon.crypto.MessageParametersMapper;
import org.ethereum.beacon.crypto.bls.milagro.MilagroParameters.Fp2;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.BytesValues;

/**
 * Message mapper that works with Milagro implementation of points of elliptic curve defined over
 * <code>F<sub>q<sup>2</sup></sub></code>.
 *
 * <p>Mapping algorithm is described in the spec <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#hash_to_g2">https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#hash_to_g2</a>
 *
 * @see MessageParametersMapper
 * @see MessageParameters
 * @see ECP2
 */
public class MilagroMessageMapper implements MessageParametersMapper<ECP2> {

  private static final BytesValue BYTES_ONE = BytesValues.ofUnsignedByte(1);
  private static final BytesValue BYTES_TWO = BytesValues.ofUnsignedByte(2);

  @Override
  public ECP2 map(MessageParameters parameters) {
    BytesValue reBytes = parameters.getHash().concat(parameters.getDomain()).concat(BYTES_ONE);
    BytesValue imBytes = parameters.getHash().concat(parameters.getDomain()).concat(BYTES_TWO);

    BIG reX = BIGs.fromBytes(Hashes.sha256(reBytes));
    BIG imX = BIGs.fromBytes(Hashes.sha256(imBytes));

    FP2 x = new FP2(reX, imX);
    ECP2 point = createPoint(x);
    while (point.is_infinity()) {
      x.add(Fp2.ONE);
      point = createPoint(x);
    }

    return ECP2s.mulByG2Cofactor(point);
  }

  private ECP2 createPoint(FP2 x) {
    FP2 y1 = RHS(x);
    if (!y1.sqrt()) {
      return new ECP2();
    } else {
      FP2 y2 = new FP2(y1);
      y2.neg();

      // return y1 if (y1_im > y2_im or (y1_im == y2_im and y1_re > y2_re)) else y2
      if (BIG.comp(y1.getB(), y2.getB()) > 0
          || (y1.getB().equals(y2.getB()) && BIG.comp(y1.getA(), y2.getA()) > 0)) {
        return new ECP2(x, y1);
      } else {
        return new ECP2(x, y2);
      }
    }
  }
}
