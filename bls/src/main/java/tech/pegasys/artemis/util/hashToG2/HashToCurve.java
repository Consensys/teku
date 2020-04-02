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

import static tech.pegasys.artemis.util.hashToG2.Helper.clear_h2_fast;
import static tech.pegasys.artemis.util.hashToG2.Helper.hashToBase;
import static tech.pegasys.artemis.util.hashToG2.Helper.isInG2;
import static tech.pegasys.artemis.util.hashToG2.Helper.isOnCurve;
import static tech.pegasys.artemis.util.hashToG2.Helper.iso3;
import static tech.pegasys.artemis.util.hashToG2.Helper.mapToCurve;

import java.nio.charset.StandardCharsets;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.tuweni.bytes.Bytes;

/**
 * This package implements the new hash-to-curve method for Ethereum 2.0.
 *
 * <p>References:
 *
 * <ul>
 *   <li>WIP PR on the Eth2 specs repository: https://github.com/ethereum/eth2.0-specs/pull/1532
 *   <li>The draft standard: https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-05
 *   <li>Reference implementations: https://github.com/algorand/bls_sigs_ref/
 *   <li>Interesting background: https://eprint.iacr.org/2019/403.pdf
 * </ul>
 *
 * <p>This code is based on the reference implementations in Python and C, and test vectors are
 * taken from there.
 *
 * <p>Note that no attempt has been made to implement constant-time methods in this package. For the
 * purposes of hashing to G2 within Ethereum I believe that this is of no consequence, since all the
 * input information is publicly known.
 */
public class HashToCurve {

  // The cipher suite is defined in the Eth2 specs
  private static final Bytes CIPHER_SUITE =
      Bytes.wrap("BLS_SIG_BLS12381G2-SHA256-SSWU-RO-_POP_".getBytes(StandardCharsets.UTF_8));

  public static boolean isInGroupG2(ECP2 point) {
    return isInG2(new JacobianPoint(point));
  }

  /**
   * Hashes to the G2 curve as described in the new BLS standard.
   *
   * @param message the message to be hashed. This is usually the 32 byte message digest
   * @param cipherSuite the salt value for HKDF_Extract
   * @return a point from the G2 group representing the message hash
   */
  public static ECP2 hashToG2(Bytes message, Bytes cipherSuite) {

    FP2Immutable u0 = hashToBase(message, (byte) 0, cipherSuite);
    FP2Immutable u1 = hashToBase(message, (byte) 1, cipherSuite);

    JacobianPoint q0 = mapToCurve(u0);
    JacobianPoint q1 = mapToCurve(u1);

    JacobianPoint p = iso3(q0.add(q1));

    // This should never fail, and the check is non-trivial, so we use an assert
    assert isOnCurve(p);

    // Use either clear_h2() or clear_h2_fast() here. The result is the same, but one is faster :)
    JacobianPoint q = clear_h2_fast(p);

    // This should never fail, and the check is very expensive, so we use an assert
    assert isInG2(q);

    return q.toECP2();
  }

  /**
   * The canonical entry point for hashing to G2.
   *
   * <p>Uses the standard cipher suite as defined in the Ethereum 2.0 specification.
   *
   * @param message the message to be hashed. This is usually the 32 byte message digest
   * @return a point from the G2 group representing the message hash
   */
  public static ECP2 hashToG2(Bytes message) {
    return hashToG2(message, CIPHER_SUITE);
  }
}
