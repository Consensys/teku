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

import static tech.pegasys.artemis.util.hashToG2.Chains.expChain;
import static tech.pegasys.artemis.util.hashToG2.Chains.h2Chain;
import static tech.pegasys.artemis.util.hashToG2.Chains.mxChain;
import static tech.pegasys.artemis.util.hashToG2.FP2Immutable.ONE;
import static tech.pegasys.artemis.util.hashToG2.IetfTools.HKDF_Expand;
import static tech.pegasys.artemis.util.hashToG2.IetfTools.HKDF_Extract;
import static tech.pegasys.artemis.util.hashToG2.Util.fpFromHex;
import static tech.pegasys.artemis.util.hashToG2.Util.negate;
import static tech.pegasys.artemis.util.hashToG2.Util.os2ip_modP;

import java.nio.charset.StandardCharsets;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.tuweni.bytes.Bytes;

public class Helper {

  // These are eighth-roots of unity
  private static final FP RV1 =
      fpFromHex(
          "0x06af0e0437ff400b6831e36d6bd17ffe48395dabc2d3435e77f76e17009241c5ee67992f72ec05f4c81084fbede3cc09");
  static final FP2Immutable[] ROOTS_OF_UNITY = {
    new FP2Immutable(new FP(1), new FP(0)),
    new FP2Immutable(new FP(0), new FP(1)),
    new FP2Immutable(RV1, RV1),
    new FP2Immutable(RV1, negate(RV1))
  };

  // 3-isogenous curve parameters
  private static final FP2Immutable Ell2p_a = new FP2Immutable(new FP(0), new FP(240));
  private static final FP2Immutable Ell2p_b = new FP2Immutable(new FP(1012), new FP(1012));

  // Distinguished non-square in Fp2 for SWU map
  private static final FP2Immutable xi_2 = new FP2Immutable(new FP(-2), new FP(-1));
  private static final FP2Immutable xi_2Pow2 = xi_2.sqr();
  private static final FP2Immutable xi_2Pow3 = xi_2Pow2.mul(xi_2);

  // Eta values, used for computing sqrt(g(X1(t)))
  private static final FP ev1 =
      fpFromHex(
          "0x0699be3b8c6870965e5bf892ad5d2cc7b0e85a117402dfd83b7f4a947e02d978498255a2aaec0ac627b5afbdf1bf1c90");
  private static final FP ev2 =
      fpFromHex(
          "0x08157cd83046453f5dd0972b6e3949e4288020b5b8a9cc99ca07e27089a2ce2436d965026adad3ef7baba37f2183e9b5");
  private static final FP ev3 =
      fpFromHex(
          "0x0ab1c2ffdd6c253ca155231eb3e71ba044fd562f6f72bc5bad5ec46a0b7a3b0247cf08ce6c6317f40edbc653a72dee17");
  private static final FP ev4 =
      fpFromHex(
          "0x0aa404866706722864480885d68ad0ccac1967c7544b447873cc37e0181271e006df72162a3d3e0287bf597fbf7f8fc1");
  private static final FP2Immutable[] etas = {
    new FP2Immutable(ev1, ev2),
    new FP2Immutable(negate(ev2), ev1),
    new FP2Immutable(ev3, ev4),
    new FP2Immutable(negate(ev4), ev3)
  };

  // Coefficients for the 3-isogeny map from Ell2' to Ell2
  private static final FP2Immutable[] XNUM = {
    new FP2Immutable(
        "0x05c759507e8e333ebb5b7a9a47d7ed8532c52d39fd3a042a88b58423c50ae15d5c2638e343d9c71c6238aaaaaaaa97d6",
        "0x05c759507e8e333ebb5b7a9a47d7ed8532c52d39fd3a042a88b58423c50ae15d5c2638e343d9c71c6238aaaaaaaa97d6"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x11560bf17baa99bc32126fced787c88f984f87adf7ae0c7f9a208c6b4f20a4181472aaa9cb8d555526a9ffffffffc71a"),
    new FP2Immutable(
        "0x11560bf17baa99bc32126fced787c88f984f87adf7ae0c7f9a208c6b4f20a4181472aaa9cb8d555526a9ffffffffc71e",
        "0x08ab05f8bdd54cde190937e76bc3e447cc27c3d6fbd7063fcd104635a790520c0a395554e5c6aaaa9354ffffffffe38d"),
    new FP2Immutable(
        "0x171d6541fa38ccfaed6dea691f5fb614cb14b4e7f4e810aa22d6108f142b85757098e38d0f671c7188e2aaaaaaaa5ed1",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  private static final FP2Immutable[] XDEN = {
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa63"),
    new FP2Immutable(
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa9f"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  private static final FP2Immutable[] YNUM = {
    new FP2Immutable(
        "0x1530477c7ab4113b59a4c18b076d11930f7da5d4a07f649bf54439d87d27e500fc8c25ebf8c92f6812cfc71c71c6d706",
        "0x1530477c7ab4113b59a4c18b076d11930f7da5d4a07f649bf54439d87d27e500fc8c25ebf8c92f6812cfc71c71c6d706"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x05c759507e8e333ebb5b7a9a47d7ed8532c52d39fd3a042a88b58423c50ae15d5c2638e343d9c71c6238aaaaaaaa97be"),
    new FP2Immutable(
        "0x11560bf17baa99bc32126fced787c88f984f87adf7ae0c7f9a208c6b4f20a4181472aaa9cb8d555526a9ffffffffc71c",
        "0x08ab05f8bdd54cde190937e76bc3e447cc27c3d6fbd7063fcd104635a790520c0a395554e5c6aaaa9354ffffffffe38f"),
    new FP2Immutable(
        "0x124c9ad43b6cf79bfbf7043de3811ad0761b0f37a1e26286b0e977c69aa274524e79097a56dc4bd9e1b371c71c718b10",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  private static final FP2Immutable[] YDEN = {
    new FP2Immutable(
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffa8fb",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffa8fb"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffa9d3"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000012",
        "0x1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaa99"),
    new FP2Immutable(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
  };

  private static final FP2Immutable[][] map_coeffs = {XNUM, XDEN, YNUM, YDEN};

  /**
   * Tests whether the given point lies on the BLS12-381 curve.
   *
   * @param p a JacobianPoint
   * @return true if the point is on the curve, false otherwise
   */
  static boolean onCurveG2(JacobianPoint p) {
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
   * Hashes a string msg of any length into an element of the FP2 field.
   *
   * <p>As defined at https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-04#section-5.3
   *
   * <p>This is hash_to_base() in the reference code.
   *
   * @param message the message to hash
   * @param ctr 0, 1, or 2 - used to efficiently create independent instances of hash_to_base
   * @param salt key for the HMAC base hash
   * @return an element in FP2
   */
  static FP2Immutable hashToBase(Bytes message, byte ctr, Bytes salt) {

    final Bytes h2cBytes = Bytes.wrap("H2C".getBytes(StandardCharsets.US_ASCII));
    final Bytes ctrBytes = Bytes.of(ctr);

    Bytes info, t;

    // Do HKDF-Extract
    Bytes m_prime = HKDF_Extract(salt, Bytes.concatenate(message, Bytes.of((byte) 0)));

    // Do first HKDF-Expand
    info = Bytes.concatenate(h2cBytes, ctrBytes, Bytes.of((byte) 1));
    t = HKDF_Expand(m_prime, info, 64);
    FP e1 = os2ip_modP(t);

    // Do second HKDF-Expand
    info = Bytes.concatenate(h2cBytes, ctrBytes, Bytes.of((byte) 2));
    t = HKDF_Expand(m_prime, info, 64);
    FP e2 = os2ip_modP(t);

    return new FP2Immutable(e1, e2);
  }

  /**
   * Calculates a point on the elliptic curve E from an element of the finite field FP2.
   *
   * <p>Curve E here is a curve isogenous to the BLS12-381 curve. Points generated by this function
   * will likely fail the onCurveG2() test.
   *
   * <p>This corresponds to osswu2_help() in the reference code.
   *
   * @param t the input field point
   * @return a point on the isogenous curve
   */
  static JacobianPoint mapToCurve(FP2Immutable t) {

    // First, compute X0(t), detecting and handling exceptional case
    FP2Immutable tPow2 = t.sqr();
    FP2Immutable tPow4 = tPow2.sqr();
    FP2Immutable num_den_common = xi_2Pow2.mul(tPow4).add(xi_2.mul(tPow2));
    FP2Immutable x0_num = Ell2p_b.mul(num_den_common.add(ONE));
    FP2Immutable x0_den = Ell2p_a.neg().mul(num_den_common);
    if (x0_den.iszilch()) {
      x0_den = Ell2p_a.mul(xi_2);
    }

    // Compute num and den of g(X0(t))
    FP2Immutable gx0_den = x0_den.pow(3);
    FP2Immutable gx0_num = Ell2p_b.mul(gx0_den);
    gx0_num = gx0_num.add(Ell2p_a.mul(x0_num).mul(x0_den.pow(2)));
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
    for (FP2Immutable fp2Immutable : ROOTS_OF_UNITY) {
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
    FP2Immutable x1_num = xi_2.mul(tPow2).mul(x0_num);
    FP2Immutable x1_den = x0_den;
    FP2Immutable tPow3 = tPow2.mul(t);
    FP2Immutable tPow6 = tPow3.sqr();
    FP2Immutable gx1_num = xi_2Pow3.mul(tPow6).mul(gx0_num);
    FP2Immutable gx1_den = gx0_den;
    sqrt_candidate = sqrt_candidate.mul(tPow3);
    for (FP2Immutable eta : etas) {
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
   * Transforms a point on the isogenous curve onto a point on the BLS12-381 curve.
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
    for (int idx = 0; idx < map_coeffs.length; idx++) {
      FP2Immutable[] coeffs = map_coeffs[idx];
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

  /**
   * Cofactor clearing.
   *
   * <p>This is compatible with the version given in section 4.1 of Budroni and Pintore, "Efficient
   * hash maps to G2 on BLS curves," ePrint 2017/419 https://eprint.iacr.org/2017/419 NOTE: this
   * impl works for Jacobian projective coordinates without computing an inversion.
   *
   * <p>This implementation avoids using the endomorphism because of US patent 7110538.
   *
   * @param p the point to be transformed to the G2 group
   * @return a corresponding point in the G2 group
   */
  static JacobianPoint clear_h2(JacobianPoint p) {
    JacobianPoint work, work3;

    // h2
    work = h2Chain(p);
    // 3 * h2
    work3 = work.add(work.dbl());
    // 3 * z * h2
    work = mxChain(work3);
    // 3 * z^2 * h2
    work = mxChain(work);
    // 3 * z^2 * h2 - 3 * h2 = 3 * (z^2 - 1) * h2
    work = work.add(work3.neg());

    return work;
  }
}
