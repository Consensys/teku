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

package tech.pegasys.artemis.util.hashToG2;

import static tech.pegasys.artemis.util.hashToG2.Helper.clear_h2;
import static tech.pegasys.artemis.util.hashToG2.Helper.hashToBase;
import static tech.pegasys.artemis.util.hashToG2.Helper.iso3;
import static tech.pegasys.artemis.util.hashToG2.Helper.mapToCurve;
import static tech.pegasys.artemis.util.hashToG2.Helper.onCurveG2;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.mikuli.G2Point;

/**
 * This package implements the new hash-to-curve method for Ethereum 2.0.
 *
 * <p>References:
 *
 * <ul>
 *   <li>WIP PR on the Eth2 specs repository: https://github.com/ethereum/eth2.0-specs/pull/1398
 *   <li>The draft standard: https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-04
 *   <li>Reference implementations: https://github.com/kwantam/bls_sigs_ref/
 *   <li>Interesting background: https://eprint.iacr.org/2019/403.pdf
 * </ul>
 *
 * <p>This code is based on the reference implementation in Python, and test vectors are taken from
 * there.
 *
 * <p>Note that no attempt has been made to implement constant-time methods in this package. For the
 * purposes of hashing to G2 within Ethereum I believe that this is of no consequence, since all the
 * input information is publicly known in any case.
 */
public class hashToCurve {

  static final Bytes CIPHER_SUITE =
      Bytes.wrap("BLS12381G2-SHA256-SSWU-RO".getBytes(StandardCharsets.UTF_8));

  /**
   * Hashes to the G2 curve as described in the new BLS standard
   *
   * <p>A draft is here:
   * https://github.com/ethereum/eth2.0-specs/blob/7e66720c06fcb6fe661c8c06ceec4474fec7a244/specs/bls_signature.md#hash_to_g2
   * (Check for updates!)
   *
   * <p>Reference code here:
   * https://github.com/kwantam/bls_sigs_ref/blob/master/python-impl/opt_swu_g2.py
   *
   * @param message the message to be hashed. This is usually the 32 byte message digest
   * @param cipherSuite the salt value for HKDF_Extract
   * @return a point from the G2 group representing the message hash
   */
  public static G2Point hashToCurve(Bytes message, Bytes cipherSuite) {

    FP2Immutable u0 = hashToBase(message, (byte) 0, cipherSuite);
    FP2Immutable u1 = hashToBase(message, (byte) 1, cipherSuite);

    JacobianPoint q0 = mapToCurve(u0);
    JacobianPoint q1 = mapToCurve(u1);

    JacobianPoint p = iso3(q0.add(q1));

    if (!onCurveG2(p)) {
      throw new RuntimeException("hashToCurve failed for unknown reasons.");
    }

    JacobianPoint q = clear_h2(p);

    return new G2Point(q.toAffine());
  }

  /**
   * The canonical entry point for hashing to G2.
   *
   * <p>Uses the standard CIPHER suite as defined in the Ethereum 2.0 specification.
   *
   * @param message the message to be hashed. This is usually the 32 byte message digest
   * @return a point from the G2 group representing the message hash
   */
  public static G2Point hashToCurve(Bytes message) {
    return hashToCurve(message, CIPHER_SUITE);
  }
}
