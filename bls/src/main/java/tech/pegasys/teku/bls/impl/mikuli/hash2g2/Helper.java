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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Chains.expChain;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Chains.mxChain;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.iwsc;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_cx;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_cx_abs;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_cy;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_qi_x;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Consts.k_qi_y;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.FP2Immutable.ONE;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Util.os2ip_modP;

import com.google.common.annotations.VisibleForTesting;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;

class Helper {

  private static final int SHA256_HASH_SIZE = 32;
  private static final int SHA256_BLOCK_SIZE = 64;

  /**
   * Tests whether the given point lies on the BLS12-381 curve.
   *
   * @param p a JacobianPoint
   * @return true if the point is on the curve, false otherwise
   */
  @VisibleForTesting
  static boolean isOnCurve(JacobianPoint p) {
    if (p.isInfinity()) {
      return true;
    }

    FP2Immutable x = p.getX();
    FP2Immutable y = p.getY();
    FP2Immutable z = p.getZ();

    FP2Immutable y2 = y.sqr();
    FP2Immutable x3 = x.pow(3);
    FP2Immutable z6 = z.pow(6);

    FP2Immutable four = new FP2Immutable(new FP(4), new FP(4));
    return y2.equals(x3.add(z6.mul(four)));
  }

  /**
   * Tests whether the given point lies in the G2 group.
   *
   * @param p a JacobianPoint
   * @return true if the point is in G2, false otherwise
   */
  static boolean isInG2(JacobianPoint p) {
    return isOnCurve(p) && mapG2ToInfinity(p).isInfinity();
  }

  /**
   * Apply a transformation that maps G2 elements, and only G2 elements, to infinity.
   *
   * <p>Uses the technique from https://eprint.iacr.org/2019/814.pdf section 3.1
   *
   * @param p the point on the curve to test
   * @return the point at infinity iff p is in G2, otherwise an arbitrary point
   */
  @VisibleForTesting
  static JacobianPoint mapG2ToInfinity(JacobianPoint p) {
    JacobianPoint psi3 = psi2(psi(p));
    return mxChain(psi3).add(psi2(p)).neg().add(p);
  }

  /**
   * Produces a uniformly random byte string of arbitrary length using SHA-256.
   *
   * <p>As defined at https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-5.3.1
   *
   * @param message the message to hash
   * @param dst the domain separation tag for the cipher suite
   * @param lengthInBytes the number of bytes we want to obtain
   * @return a uniformly random sequence of Bytes
   */
  @VisibleForTesting
  static Bytes expandMessage(Bytes message, Bytes dst, int lengthInBytes) {
    checkArgument(dst.size() < 256, "The DST must be 255 bytes or fewer.");
    checkArgument(lengthInBytes > 0, "Number of bytes requested must be greater than zero.");

    final int ell = 1 + (lengthInBytes - 1) / SHA256_HASH_SIZE;
    checkArgument(ell <= 255, "Too many bytes of output were requested.");

    byte[] uniformBytes = new byte[ell * SHA256_HASH_SIZE];

    Bytes dstPrime = Bytes.concatenate(dst, Bytes.of((byte) dst.size()));
    Bytes zPad = Bytes.wrap(new byte[SHA256_BLOCK_SIZE]);
    Bytes libStr = Bytes.ofUnsignedShort(lengthInBytes);
    Bytes b0 =
        Hash.sha2_256(Bytes.concatenate(zPad, message, libStr, Bytes.of((byte) 0), dstPrime));
    Bytes bb = Hash.sha2_256(Bytes.concatenate(b0, Bytes.of((byte) 1), dstPrime));
    System.arraycopy(bb.toArrayUnsafe(), 0, uniformBytes, 0, SHA256_HASH_SIZE);
    for (int i = 1; i < ell; i++) {
      bb = Hash.sha2_256(Bytes.concatenate(b0.xor(bb), Bytes.of((byte) (i + 1)), dstPrime));
      System.arraycopy(bb.toArrayUnsafe(), 0, uniformBytes, i * SHA256_HASH_SIZE, SHA256_HASH_SIZE);
    }
    return Bytes.wrap(uniformBytes, 0, lengthInBytes);
  }

  /**
   * Hashes a string msg of any length into one or more elements of the FP2 field.
   *
   * <p>As defined at https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-5.2
   *
   * @param message the message to hash
   * @param count the number of field elements to return
   * @param dst the domain separation tag for the cipher suite
   * @return an element in FP2
   */
  static FP2Immutable[] hashToField(Bytes message, int count, Bytes dst) {

    // See https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-8.8.1
    final int l = 64;
    // The extension degree of our field, FP2
    final int m = 2;

    final int lenInBytes = count * m * l;
    final Bytes uniformBytes = expandMessage(message, dst, lenInBytes);
    FP2Immutable[] u = new FP2Immutable[count];

    for (int i = 0; i < count; i++) {
      FP e0 = os2ip_modP(uniformBytes.slice(l * i * m, l));
      FP e1 = os2ip_modP(uniformBytes.slice(l * (1 + i * m), l));
      u[i] = new FP2Immutable(e0, e1);
    }

    return u;
  }

  /**
   * Calculates a point on the elliptic curve E' from an element of the finite field FP2.
   *
   * <p>Curve E' here is a curve isogenous to the BLS12-381 curve. The isogenous curve parameters
   * are here: https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-8.8.2
   *
   * @param t the input field point
   * @return a point on the isogenous curve
   */
  static JacobianPoint mapToCurve(FP2Immutable t) {

    // First, compute X0(t), detecting and handling exceptional case
    FP2Immutable tPow2 = t.sqr();
    FP2Immutable tPow4 = tPow2.sqr();
    FP2Immutable num_den_common = Consts.xi_2Pow2.mul(tPow4).add(Consts.xi_2.mul(tPow2));
    FP2Immutable x0_num = Consts.Ell2p_b.mul(num_den_common.add(ONE));
    FP2Immutable x0_den = Consts.Ell2p_a.neg().mul(num_den_common);
    if (x0_den.iszilch()) {
      x0_den = Consts.Ell2p_a.mul(Consts.xi_2);
    }

    // Compute num and den of g(X0(t))
    FP2Immutable gx0_den = x0_den.pow(3);
    FP2Immutable gx0_num = Consts.Ell2p_b.mul(gx0_den);
    gx0_num = gx0_num.add(Consts.Ell2p_a.mul(x0_num).mul(x0_den.pow(2)));
    gx0_num = gx0_num.add(x0_num.pow(3));

    // try taking sqrt of g(X0(t))
    // this uses the trick for combining division and sqrt from Section 5 of
    // Bernstein, Duif, Lange, Schwabe, and Yang, "High-speed high-security signatures."
    // J Crypt Eng 2(2):77--89, Sept. 2012. http://ed25519.cr.yp.to/ed25519-20110926.pdf
    FP2Immutable tmp1, tmp2;
    tmp1 = gx0_den.pow(7); // v^7
    tmp2 = gx0_num.mul(tmp1); // u v^7
    tmp1 = tmp1.mul(tmp2).mul(gx0_den); // u v^15
    FP2Immutable sqrt_candidate = tmp2.mul(expChain(tmp1));

    // check if g(X0(t)) is square and return the sqrt if so
    for (FP2Immutable fp2Immutable : Consts.ROOTS_OF_UNITY) {
      FP2Immutable y0 = sqrt_candidate.mul(fp2Immutable);
      if (y0.sqr().mul(gx0_den).equals(gx0_num)) {
        // found sqrt(g(X0(t))). force sign of y to equal sign of t
        if (t.sgn0() != y0.sgn0()) {
          y0 = y0.neg();
        }
        return new JacobianPoint(x0_num.mul(x0_den), y0.mul(x0_den.pow(3)), x0_den);
      }
    }

    // if we've gotten here, then g(X0(t)) is not square. convert srqt_candidate to sqrt(g(X1(t)))
    FP2Immutable x1_num = Consts.xi_2.mul(tPow2).mul(x0_num);
    FP2Immutable x1_den = x0_den;
    FP2Immutable tPow3 = tPow2.mul(t);
    FP2Immutable tPow6 = tPow3.sqr();
    FP2Immutable gx1_num = Consts.xi_2Pow3.mul(tPow6).mul(gx0_num);
    FP2Immutable gx1_den = gx0_den;
    sqrt_candidate = sqrt_candidate.mul(tPow3);
    for (FP2Immutable eta : Consts.etas) {
      FP2Immutable y1 = eta.mul(sqrt_candidate);
      if (y1.sqr().mul(gx1_den).equals(gx1_num)) {
        // found sqrt(g(X1(t))). force sign of y to equal sign of t
        if (t.sgn0() != y1.sgn0()) {
          y1 = y1.neg();
        }
        return new JacobianPoint(x1_num.mul(x1_den), y1.mul(x1_den.pow(3)), x1_den);
      }
    }

    // Should never be reached
    throw new RuntimeException("mapToCurve failed for unknown reasons.");
  }

  /**
   * Maps a point on the isogenous curve E' onto a point on the BLS12-381 curve.
   *
   * <p>This function evaluates the isogeny over Jacobian projective coordinates. For details, see
   * Section 4.3 of Wahby and Boneh, "Fast and simple constant-time hashing to the BLS12-381
   * elliptic curve ePrint # 2019/403, https://ia.cr/2019/403.
   *
   * @param p input point on isogenous curve
   * @return output point on BLS12-381
   */
  static JacobianPoint iso3(JacobianPoint p) {
    FP2Immutable x = new FP2Immutable(p.getX());
    FP2Immutable y = new FP2Immutable(p.getY());
    FP2Immutable z = new FP2Immutable(p.getZ());
    FP2Immutable[] mapvals = new FP2Immutable[4];

    // precompute the required powers of Z^2
    final FP2Immutable[] zpows = new FP2Immutable[4];
    zpows[0] = ONE;
    zpows[1] = z.sqr();
    zpows[2] = zpows[1].sqr();
    zpows[3] = zpows[2].mul(zpows[1]);

    // compute the numerator and denominator of the X and Y maps via Horner's rule
    FP2Immutable[] coeffs_z = new FP2Immutable[4];
    for (int idx = 0; idx < Consts.map_coeffs.length; idx++) {
      FP2Immutable[] coeffs = Consts.map_coeffs[idx];
      coeffs_z[0] = coeffs[coeffs.length - 1];
      for (int j = 1; j < coeffs.length; j++) {
        coeffs_z[j] = coeffs[coeffs.length - j - 1].mul(zpows[j]);
      }
      FP2Immutable tmp = coeffs_z[0];
      for (int j = 1; j < coeffs.length; j++) {
        tmp = tmp.mul(x).add(coeffs_z[j]);
      }
      mapvals[idx] = tmp;
    }

    // xden is of order 1 less than xnum, so need to multiply it by an extra factor of Z^2
    mapvals[1] = mapvals[1].mul(zpows[1]);

    // multiply result of Y map by the y-coordinate y / z^3
    mapvals[2] = mapvals[2].mul(y);
    mapvals[3] = mapvals[3].mul(z.pow(3));

    FP2Immutable zz = mapvals[1].mul(mapvals[3]);
    FP2Immutable xx = mapvals[0].mul(mapvals[3]).mul(zz);
    FP2Immutable yy = mapvals[2].mul(mapvals[1]).mul(zz.sqr());

    return new JacobianPoint(xx, yy, zz);
  }

  /** Shortcut Frobenius evaluation that avoids going all the way to Fq12 */
  private static FP2Immutable qi_x(FP2Immutable x) {
    FP a = new FP(x.getFp2().getA());
    FP b = new FP(x.getFp2().getB());
    a.mul(k_qi_x);
    b.mul(k_qi_x);
    b.neg();
    return new FP2Immutable(new FP2(a, b));
  }

  /** Shortcut Frobenius evaluation that avoids going all the way to Fq12 */
  private static FP2Immutable qi_y(FP2Immutable y) {
    FP y0 = new FP(y.getFp2().getA());
    FP y1 = new FP(y.getFp2().getB());
    FP a = new FP(y0);
    FP b = new FP(y0);
    a.add(y1);
    a.mul(k_qi_y);
    b.sub(y1);
    b.mul(k_qi_y);
    return new FP2Immutable(new FP2(a, b));
  }

  /** The untwist-Frobenius-twist endomorphism */
  @VisibleForTesting
  static JacobianPoint psi(JacobianPoint p) {
    FP2Immutable x = p.getX();
    FP2Immutable y = p.getY();
    FP2Immutable z = p.getZ();

    FP2Immutable z2 = z.sqr();
    FP2Immutable px = k_cx.mul(qi_x(iwsc.mul(x)));
    FP2Immutable pz2 = qi_x(iwsc.mul(z2));
    FP2Immutable py = k_cy.mul(qi_y(iwsc.mul(y)));
    FP2Immutable pz3 = qi_y(iwsc.mul(z2).mul(z));

    FP2Immutable zOut = pz2.mul(pz3);
    FP2Immutable xOut = px.mul(pz3).mul(zOut);
    FP2Immutable yOut = py.mul(pz2).mul(zOut.sqr());

    return new JacobianPoint(xOut, yOut, zOut);
  }

  /** Optimised calculation for psi^2(p) */
  @VisibleForTesting
  static JacobianPoint psi2(JacobianPoint p) {
    return new JacobianPoint(p.getX().mul(k_cx_abs), p.getY().neg(), p.getZ());
  }

  /**
   * Cofactor clearing
   *
   * <p>Uses the version given in section 4.1 of Budroni and Pintore, "Efficient hash maps to G2 on
   * BLS curves," ePrint 2017/419 https://eprint.iacr.org/2017/419 NOTE: this impl works for
   * Jacobian projective coordinates without computing an inversion.
   *
   * <p>This is equivalent to multiplying by h_eff from
   * https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-8.8.2
   *
   * @param p the point to be transformed to the G2 group
   * @return a corresponding point in the G2 group
   */
  static JacobianPoint clearH2(JacobianPoint p) {
    // (-x + 1) P
    JacobianPoint work = mxChain(p).add(p);
    // -psi(P)
    JacobianPoint minus_psi_p = psi(p).neg();
    // (-x + 1) P - psi(P)
    work = work.add(minus_psi_p);
    // (x^2 - x) P + x psi(P)
    work = mxChain(work);
    // (x^2 - x) P + (x - 1) psi(P)
    work = work.add(minus_psi_p);
    // (x^2 - x - 1) P + (x - 1) psi(P)
    work = work.add(p.neg());
    // psi(psi(2P))
    JacobianPoint psi_psi_2p = psi2(p.dbl());
    // (x^2 - x - 1) P + (x - 1) psi(P) + psi(psi(2P))
    work = work.add(psi_psi_2p);

    return work;
  }
}
