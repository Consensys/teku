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

package tech.pegasys.teku.bls.impl.mikuli.hash2g2;

import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.clearH2;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.hashToField;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.isInG2;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.isOnCurve;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.iso3;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.mapToCurve;

import java.nio.charset.StandardCharsets;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.tuweni.bytes.Bytes;

/**
 * This package implements the new hash-to-curve method for Ethereum 2.0.
 *
 * <p>References:
 *
 * <ul>
 *   <li>The draft standard: https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07
 *   <li>Reference implementations: https://github.com/algorand/bls_sigs_ref/
 *   <li>Interesting background: https://eprint.iacr.org/2019/403.pdf
 * </ul>
 *
 * <p>This code is partially based on the reference implementations in Python and C, and test
 * vectors are taken from there.
 *
 * <p>Note that no attempt has been made to implement constant-time methods in this package. For the
 * purposes of hashing to G2 within Ethereum I believe that this is of no consequence, since all the
 * input information is publicly known.
 */
public class HashToCurve {

  // The ciphersuite defined in the Eth2 specification which also serves as domain separation tag
  // https://github.com/ethereum/eth2.0-specs/blob/v0.12.0/specs/phase0/beacon-chain.md#bls-signatures
  public static final Bytes ETH2_DST =
      Bytes.wrap("BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_POP_".getBytes(StandardCharsets.US_ASCII));

  /**
   * Check whether a Milagro ECP2 point is in group G2.
   *
   * <p>This package provides a fast method for performing the check.
   *
   * @param point a Milagro ECP2 point
   * @return true if the point is in G2, false otherwise
   */
  public static boolean isInGroupG2(ECP2 point) {
    return isInG2(new JacobianPoint(point));
  }

  /**
   * Hashes arbitrary data to the G2 curve as per the draft IETF standard v07:
   * https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07
   *
   * <p>The basic method is as described in
   * https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-3
   *
   * <ol>
   *   <li>u = hash_to_field(msg, 2)
   *   <li>Q0 = map_to_curve(u[0])
   *   <li>Q1 = map_to_curve(u[1])
   *   <li>R = Q0 + Q1 # Point addition
   *   <li>P = clear_cofactor(R)
   *   <li>return P
   * </ol>
   *
   * <p>Since the 3-isogeny map commutes with point addition, as an optimisation we apply it after
   * step 4 rather than separately in steps 2 and 3. See the note in
   * https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-6.6.3
   *
   * <p>The specifics for hashing to BLS12-381 G2 are described in
   * https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-8.8.2
   *
   * <ul>
   *   <li>hash_to_field (hashToField) is as defined in
   *       https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-5.2 where the
   *       expand_message function is expand_message_xmd,
   *       https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-5.3.1
   *   <li>map_to_curve (mapToCurve) is "Simplified SWU for AB == 0",
   *       https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-6.6.3
   *   <li>iso_map (iso3) is the "3-isogeny map",
   *       https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#appendix-C.3
   *   <li>clear_cofactor (clearH2) uses the method of
   *       https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#appendix-D.4
   * </ul>
   *
   * @param message the message to be hashed. This is usually the 32 byte message digest
   * @param dst the domain separation tag (DST)
   * @return a point from the G2 group representing the message hash
   */
  public static ECP2 hashToG2(Bytes message, Bytes dst) {

    FP2Immutable[] u = hashToField(message, 2, dst);

    JacobianPoint q0 = mapToCurve(u[0]);
    JacobianPoint q1 = mapToCurve(u[1]);

    JacobianPoint r = iso3(q0.add(q1));

    // This should never fail, and the check is non-trivial, so we use an assert
    assert isOnCurve(r);

    JacobianPoint p = clearH2(r);

    // This should never fail, and the check is very expensive, so we use an assert
    assert isInG2(p);

    return p.toECP2();
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
    return hashToG2(message, ETH2_DST);
  }
}
