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

import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Util.bigFromHex;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Util.fpFromHex;

import java.util.Objects;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.FP2;

/**
 * It's safer and simpler to wrap FP2 objects into an immutable type. The underlying FP2 type is
 * highly mutable. As a benefit, this class allows us to chain field operations nicely - this makes
 * dealing with Milagro much less tiresome.
 *
 * <p>The compiler seems to do a decent job of handling the continual wrapping and unwrapping in
 * practice.
 */
final class FP2Immutable {

  static final FP2Immutable ZERO = new FP2Immutable(0);
  static final FP2Immutable ONE = new FP2Immutable(1);

  // The threshold for ordering elements is (P - 1) // 2
  static final BIG THRESHOLD =
      bigFromHex(
          "0x0d0088f51cbff34d258dd3db21a5d66bb23ba5c279c2895fb39869507b587b120f55ffff58a9ffffdcff7fffffffd555");

  private final FP2 fp2;

  /**
   * Copy constructor
   *
   * @param fp2Immutable the FP2Immutable object to copy
   */
  FP2Immutable(FP2Immutable fp2Immutable) {
    fp2 = fp2Immutable.getFp2();
  }

  /**
   * Construct from an existing FP2 object.
   *
   * @param fp2 the FP2 object to copy
   */
  FP2Immutable(FP2 fp2) {
    this.fp2 = new FP2(fp2);
  }

  /**
   * Construct from two FP objects.
   *
   * @param fpA the first element
   * @param fpB the second element
   */
  FP2Immutable(FP fpA, FP fpB) {
    fp2 = new FP2(fpA, fpB);
  }

  /**
   * Construct from two BIG objects.
   *
   * @param big1 the first element
   * @param big2 the second element
   */
  FP2Immutable(BIG big1, BIG big2) {
    fp2 = new FP2(big1, big2);
  }

  /**
   * Construct from an integer.
   *
   * <p>The first element is set to the given integer, the second to zero.
   *
   * @param c integer to set the first element to
   */
  FP2Immutable(int c) {
    fp2 = new FP2(c);
  }

  /**
   * Construct from hexadecimal strings (prefixed "0x").
   *
   * @param hex1 hexadecimal representation of the first element
   * @param hex2 hexadecimal representation of the first element
   */
  FP2Immutable(String hex1, String hex2) {
    fp2 = new FP2(fpFromHex(hex1), fpFromHex(hex2));
  }

  /** Wrap FP2 sqr() method to return a result */
  FP2Immutable sqr() {
    FP2 result = new FP2(fp2);
    result.sqr();
    return new FP2Immutable(result);
  }

  /** Wrap FP2 mul() method to return a result */
  FP2Immutable mul(FP2Immutable a) {
    FP2 result = new FP2(fp2);
    result.mul(a.fp2);
    return new FP2Immutable(result);
  }

  /** Wrap FP2 imul() method to return a result */
  FP2Immutable mul(int c) {
    FP2 result = new FP2(fp2);
    result.imul(c);
    result.norm();
    return new FP2Immutable(result);
  }

  /** Wrap FP2 pmul() method to return a result */
  FP2Immutable mul(FP c) {
    FP2 result = new FP2(fp2);
    result.pmul(c);
    result.norm();
    return new FP2Immutable(result);
  }

  /** Wrap FP2 add() method to return a result */
  FP2Immutable add(FP2Immutable a) {
    FP2 result = new FP2(fp2);
    result.add(a.fp2);
    result.norm();
    return new FP2Immutable(result);
  }

  /** Wrap FP2 sub() method to return a result */
  FP2Immutable sub(FP2Immutable a) {
    FP2 result = new FP2(fp2);
    result.sub(a.fp2);
    result.norm();
    return new FP2Immutable(result);
  }

  /** Wrap FP2 neg() method to return a result */
  FP2Immutable neg() {
    FP2 result = new FP2(fp2);
    result.neg();
    return new FP2Immutable(result);
  }

  /** Wrap FP2 reduce() method to return a result */
  FP2Immutable reduce() {
    FP2 result = new FP2(fp2);
    result.reduce();
    return new FP2Immutable(result);
  }

  /** Wrap FP2 inverse() method to return a result */
  FP2Immutable inverse() {
    FP2 result = new FP2(fp2);
    result.inverse();
    return new FP2Immutable(result);
  }

  /** Return double the element */
  FP2Immutable dbl() {
    return this.add(this);
  }

  /** Test whether the element is zero */
  boolean iszilch() {
    return fp2.iszilch();
  }

  /**
   * Calculate the "sign" of the field element in constant time.
   *
   * <p>Defined here: https://tools.ietf.org/html/draft-irtf-cfrg-hash-to-curve-07#section-4.1
   *
   * @return zero or one
   */
  int sgn0() {
    final int sign0 = fp2.getA().parity();
    final int zero0 = fp2.getA().iszilch() ? 1 : 0;
    final int sign1 = fp2.getB().parity();
    return sign0 | (zero0 & sign1);
  }

  /**
   * Calculate the element to the power of (2 ^ n)
   *
   * @param n the number of times to square the element
   * @return the element to the power of (2 ^ n)
   */
  FP2Immutable sqrs(int n) {
    FP2 result = new FP2(fp2);
    while (n-- > 0) {
      result.sqr();
    }
    result.norm();
    return new FP2Immutable(result);
  }

  /**
   * Raise this element to an integer exponent.
   *
   * @param exponent the exponent
   * @return the field point raised to the exponent
   */
  FP2Immutable pow(int exponent) {
    if (exponent == 0) return ONE;
    if (exponent == 1) return this;
    if (exponent == 2) return this.sqr();
    FP2 res = new FP2(1);
    FP2 tmp = new FP2(fp2);
    while (exponent > 0) {
      if ((exponent & 1) == 1) {
        res.mul(tmp);
      }
      tmp.sqr();
      exponent >>>= 1;
    }
    return new FP2Immutable(res);
  }

  FP2 getFp2() {
    // Return a copy to preserve immutability
    return new FP2(fp2);
  }

  @Override
  public String toString() {
    return fp2.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FP2Immutable)) {
      return false;
    }
    FP2Immutable other = (FP2Immutable) obj;
    return this.fp2.equals(other.fp2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.reduce().toString());
  }
}
