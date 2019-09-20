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

import static tech.pegasys.artemis.util.hashToG2.FP2Immutable.ONE;
import static tech.pegasys.artemis.util.hashToG2.Util.bigFromBigInt;
import static tech.pegasys.artemis.util.hashToG2.Util.bigFromHex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Helper {

  static final int SHA256_HASH_SIZE = 32;

  // The field modulus
  static final BIG P = new BIG(ROM.Modulus);

  // These are eighth-roots of unity
  static final BIG RV1 =
      bigFromHex(
          "0x06af0e0437ff400b6831e36d6bd17ffe48395dabc2d3435e77f76e17009241c5ee67992f72ec05f4c81084fbede3cc09");
  static final FP2Immutable[] ROOTS_OF_UNITY = {
    new FP2Immutable(new BIG(1), new BIG(0)),
    new FP2Immutable(new BIG(0), new BIG(1)),
    new FP2Immutable(RV1, RV1),
    new FP2Immutable(RV1, P.minus(RV1))
  };

  // 3-isogenous curve parameters
  private static final FP2Immutable Ell2p_a = new FP2Immutable(new BIG(0), new BIG(240));
  private static final FP2Immutable Ell2p_b = new FP2Immutable(new BIG(1012), new BIG(1012));

  // Distinguished non-square in Fp2 for SWU map
  private static final FP2Immutable xi_2 = new FP2Immutable(new BIG(1), new BIG(1));
  private static final FP2Immutable xi_2Pow2 = xi_2.sqr();
  private static final FP2Immutable xi_2Pow3 = xi_2Pow2.mul(xi_2);

  // Eta values, used for computing sqrt(g(X1(t)))
  private static final BIG ev1 =
      bigFromHex(
          "0x02c4a7244a026bd3e305cc456ad9e235ed85f8b53954258ec8186bb3d4eccef7c4ee7b8d4b9e063a6c88d0aa3e03ba01");
  private static final BIG ev2 =
      bigFromHex(
          "0x085fa8cd9105715e641892a0f9a4bb2912b58b8d32f26594c60679cc7973076dc6638358daf3514d6426a813ae01f51a");
  private static final FP2Immutable[] etas = {
    new FP2Immutable(ev1, new BIG(0)),
    new FP2Immutable(new BIG(0), ev1),
    new FP2Immutable(ev2, ev2),
    new FP2Immutable(ev2, P.minus(ev2))
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

  // Constants for Psi, the untwist-Frobenius-twist endomorphism
  private static final BIG iwscBase =
      bigFromHex(
          "0x0d0088f51cbff34d258dd3db21a5d66bb23ba5c279c2895fb39869507b587b120f55ffff58a9ffffdcff7fffffffd556");
  private static final FP2Immutable iwsc = new FP2Immutable(iwscBase, iwscBase.minus(new BIG(1)));
  private static final BIG k_qi_x =
      bigFromHex(
          "0x1a0111ea397fe699ec02408663d4de85aa0d857d89759ad4897d29650fb85f9b409427eb4f49fffd8bfd00000000aaad");
  private static final BIG k_qi_y =
      bigFromHex(
          "0x06af0e0437ff400b6831e36d6bd17ffe48395dabc2d3435e77f76e17009241c5ee67992f72ec05f4c81084fbede3cc09");
  private static final FP2Immutable k_cx = new FP2Immutable(new BIG(0), k_qi_x);
  private static final FP2Immutable k_cy =
      new FP2Immutable(
          bigFromHex(
              "0x135203e60180a68ee2e9c448d77a2cd91c3dedd930b1cf60ef396489f61eb45e304466cf3e67fa0af1ee7b04121bdea2"),
          k_qi_y);

  // The enormous exponent used in mapToCurve
  private static final BIG THREE = new BIG(3);
  private static final DBIGExtended EXPONENT =
      new DBIGExtended(BIG.mul(P.plus(THREE), P.minus(THREE))).fshr(4);

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

    FP2Immutable four = new FP2Immutable(new BIG(4), new BIG(4));
    return y2.equals(x3.add(z6.mul(four)));
  }

  /**
   * Standard HMAC SHA-256 based on https://tools.ietf.org/html/rfc2104
   *
   * @param text the data to be hashed
   * @param key the key
   * @return Bytes of the HMAC SHA-256 of the text with key
   */
  static Bytes HMAC_SHA256(byte[] text, byte[] key) {

    Security.addProvider(new BouncyCastleProvider());

    // SHA256 blocksize in bytes
    int blockSize = 64;
    byte ipad = (byte) 0x36;
    byte opad = (byte) 0x5c;

    if (key.length > blockSize) {
      key = Hash.sha2_256(key);
    }

    // Pad or truncate the key to the blocksize
    byte[] ikmPadded = new byte[blockSize];
    for (int i = 0; i < key.length; i++) {
      ikmPadded[i] = key[i];
    }

    byte[] ikmXorIpad = new byte[blockSize];
    byte[] ikmXorOpad = new byte[blockSize];
    for (int i = 0; i < blockSize; i++) {
      ikmXorIpad[i] = (byte) (ikmPadded[i] ^ ipad);
      ikmXorOpad[i] = (byte) (ikmPadded[i] ^ opad);
    }

    return Hash.sha2_256(
        Bytes.concatenate(
            Bytes.wrap(ikmXorOpad),
            Hash.sha2_256(Bytes.concatenate(Bytes.wrap(ikmXorIpad), Bytes.wrap(text)))));
  }

  /**
   * Standard HKDF_Extract as defined at https://tools.ietf.org/html/rfc5869#section-2.2
   *
   * <p>Note that the arguments to HMAC_SHA-256 appear to be inverted in RFC5869.
   *
   * @param salt salt value (a non-secret random value)
   * @param ikm input keying material
   * @return a pseudorandom key (of HashLen octets)
   */
  static Bytes HKDF_Extract(Bytes salt, Bytes ikm) {
    return HMAC_SHA256(ikm.toArray(), salt.toArray());
  }

  /**
   * Standard HKDF_Expand as defined at https://tools.ietf.org/html/rfc5869#section-2.3
   *
   * @param prk a pseudorandom key of at least HashLen octets
   * @param info optional context and application specific information
   * @param length desired length of output keying material in octets
   * @return output keying material (of `length` octets)
   */
  static Bytes HKDF_Expand(Bytes prk, Bytes info, int length) {
    Bytes okm = Bytes.EMPTY;
    Bytes tOld = Bytes.EMPTY;
    int i = 1;
    int remainder = length;
    while (remainder > 0) {
      Bytes tNew =
          HMAC_SHA256(Bytes.concatenate(tOld, info, Bytes.of((byte) i)).toArray(), prk.toArray());
      okm = Bytes.concatenate(okm, tNew);
      i += 1;
      remainder -= SHA256_HASH_SIZE;
      tOld = tNew;
    }
    return okm.slice(0, length);
  }

  /**
   * Big-endian conversion of byte array into a positive BigInteger.
   *
   * <p>As defined at https://tools.ietf.org/html/rfc3447#section-4.2
   *
   * @param b octet string to be converted
   * @return corresponding nonnegative integer
   */
  static BigInteger os2ip(Bytes b) {
    return new BigInteger(1, b.toArray());
  }

  /**
   * Hashes a string msg of any length into an element of the FP2 field.
   *
   * <p>As defined at https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-04#section-5.3
   *
   * @param message the message to hash
   * @param ctr 0, 1, or 2 - used to efficiently create independent instances of hash_to_base
   * @param salt key for the HMAC base hash
   * @return an element in FP2
   */
  static FP2Immutable hashToBase(Bytes message, byte ctr, Bytes salt) {

    final BIG curveOrder = new BIG(ROM.Modulus);
    Bytes info, t;

    // The output of HKDF_Expand is 64 bytes, which is too large for a BIG, so we use BigInteger
    BigInteger tBig;
    BigInteger modulus = new BigInteger(P.toString(), 16);

    // Do HKDF-Extract
    Bytes m_prime = HKDF_Extract(salt, message);

    // Do first HKDF-Expand
    info =
        Bytes.concatenate(
            Bytes.wrap("H2C".getBytes(StandardCharsets.US_ASCII)),
            Bytes.of(ctr),
            Bytes.of((byte) 1));
    t = HKDF_Expand(m_prime, info, 64);

    tBig = os2ip(t).mod(modulus);
    BIG e1 = bigFromBigInt(tBig);

    // Do second HKDF-Expand
    info =
        Bytes.concatenate(
            Bytes.wrap("H2C".getBytes(StandardCharsets.US_ASCII)),
            Bytes.of(ctr),
            Bytes.of((byte) 2));
    t = HKDF_Expand(m_prime, info, 64);

    tBig = os2ip(t).mod(modulus);
    BIG e2 = bigFromBigInt(tBig);

    return new FP2Immutable(e1, e2);
  }

  /**
   * Calculates a point on the elliptic curve E from an element of the finite field FP2.
   *
   * <p>Curve E here is a curve isogenous to the BLS12-381 curve. Points generated by this function
   * will likely fail the onCurveG2() test.
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
    FP2Immutable sqrt_candidate = tmp2.mul(tmp1.pow(EXPONENT));

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
    FP2Immutable mapvals[] = new FP2Immutable[4];

    // precompute the required powers of Z^2
    FP2Immutable zpows[] = new FP2Immutable[4];
    zpows[0] = ONE;
    zpows[1] = z.sqr();
    zpows[2] = zpows[1].mul(zpows[1]);
    zpows[3] = zpows[2].mul(zpows[1]);

    // compute the numerator and denominator of the X and Y maps via Horner's rule
    FP2Immutable[] coeffs_z = new FP2Immutable[4];
    for (int idx = 0; idx < map_coeffs.length; idx++) {
      FP2Immutable[] coeffs = map_coeffs[idx];
      for (int j = 0; j < coeffs.length; j++) {
        coeffs_z[j] = coeffs[coeffs.length - j - 1].mul(zpows[j]);
      }
      FP2Immutable tmp = coeffs_z[0];
      for (int j = 1; j < coeffs.length; j++) {
        tmp = tmp.mul(x);
        tmp = tmp.add(coeffs_z[j]);
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
    FP2Immutable yy = mapvals[2].mul(mapvals[1]).mul(zz).mul(zz);

    return new JacobianPoint(xx, yy, zz);
  }

  /**
   * Addition chain for multiplication by 0xd201000000010000 == -x, the BLS parameter.
   *
   * @param p the point to be multiplied
   * @return -x * p
   */
  static JacobianPoint mx_chain(JacobianPoint p) {
    JacobianPoint q = p.dbl();
    int[] ndoubles = {2, 3, 9, 32, 16};
    for (int n : ndoubles) {
      q = q.add(p);
      for (int j = 0; j < n; j++) {
        q = q.dbl();
      }
    }
    return q;
  }

  // Shortcut Frobenius evaluation that avoids going all the way to Fq12
  static FP2Immutable qi_x(FP2Immutable x) {
    BIG a = BIG.mul(k_qi_x, x.getFp2().getA()).mod(P);
    BIG b = P.minus(BIG.mul(k_qi_x, x.getFp2().getB()).mod(P));
    return new FP2Immutable(a, b);
  }

  // Shortcut Frobenius evaluation that avoids going all the way to Fq12
  static FP2Immutable qi_y(FP2Immutable y) {
    // TODO: what a mess! y0 - y1 causes havoc when it is negative. Also be careful about
    // immutability
    BIG y0;
    BIG y1 = new BIG(y.getFp2().getB());
    y0 = y.getFp2().getA();
    BIG y0py1 = y0;
    y0py1.add(y1);
    y0 = y.getFp2().getA();
    BIG y0my1 = y0;
    y0.add(P);
    y0my1.sub(y1);
    BIG a = BIG.mul(k_qi_y, y0py1).mod(P);
    BIG b = BIG.mul(k_qi_y, y0my1).mod(P);
    return new FP2Immutable(a, b);
  }

  // The untwist-Frobenius-twist endomorphism
  static JacobianPoint psi(JacobianPoint p) {
    FP2Immutable x = new FP2Immutable(p.getX());
    FP2Immutable y = new FP2Immutable(p.getY());
    FP2Immutable z = new FP2Immutable(p.getZ());

    FP2Immutable z2 = z.sqr();
    FP2Immutable px = k_cx.mul(qi_x(iwsc.mul(x)));
    FP2Immutable pz2 = qi_x(iwsc.mul(z2));
    FP2Immutable py = k_cy.mul(qi_y(iwsc.mul(y)));
    FP2Immutable pz3 = qi_y(iwsc.mul(z2).mul(z));

    FP2Immutable zOut = pz2.mul(pz3);
    FP2Immutable xOut = px.mul(pz3).mul(zOut);
    FP2Immutable yOut = py.mul(pz2).mul(zOut).mul(zOut);

    return new JacobianPoint(xOut, yOut, zOut);
  }

  /**
   * Fast cofactor clearing using the untwist-Frobenius-twist Endomorphism.
   *
   * <p>We use the version given in section 4.1 of Budroni and Pintore, "Efficient hash maps to G2
   * on BLS curves," ePrint 2017/419 https://eprint.iacr.org/2017/419 NOTE: this impl works for
   * Jacobian projective coordinates without computing an inversion.
   *
   * @param p the point to be transformed to the G2 group
   * @return a corresponding point in the G2 group
   */
  static JacobianPoint clear_h2(JacobianPoint p) {
    // (-x + 1) P
    JacobianPoint work = mx_chain(p).add(p);
    // -psi(P)
    JacobianPoint minus_psi_p = psi(p).neg();
    // (-x + 1) P - psi(P)
    work = work.add(minus_psi_p);
    // (x^2 - x) P + x psi(P)
    work = mx_chain(work);
    // (x^2 - x) P + (x - 1) psi(P)
    work = work.add(minus_psi_p);
    // (x^2 - x - 1) P + (x - 1) psi(P)
    work = work.add(p.neg());
    // psi(psi(2P))
    JacobianPoint psi_psi_2p = psi(psi(p.dbl()));
    // (x^2 - x - 1) P + (x - 1) psi(P) + psi(psi(2P))
    work = work.add(psi_psi_2p);

    return work;
  }
}
