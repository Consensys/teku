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

import static tech.pegasys.artemis.util.hashToG2.Util.P;
import static tech.pegasys.artemis.util.hashToG2.Util.bigFromHex;

import java.util.Objects;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;
import org.apache.milagro.amcl.BLS381.FP2;

/**
 * It's safer and simpler to wrap FP2 objects into an immutable type. The underlying FP2 type is
 * highly mutable. As a benefit, this class allows us to chain field operations nicely - this makes
 * dealing with Milagro much less tiresome.
 */
public class FP2Immutable {

  static final FP2Immutable ZERO = new FP2Immutable(0);
  static final FP2Immutable ONE = new FP2Immutable(1);

  /**
   * Raise a field point to an integer exponent.
   *
   * @param x the field point
   * @param exponent the exponent
   * @return the field point raised to the exponent and reduced
   */
  static FP2Immutable pow(FP2Immutable x, int exponent) {
    if (exponent == 0) return ONE;
    if (exponent == 1) return x;
    if (exponent == 2) return x.sqr().reduce();
    FP2Immutable result = ONE;
    while (exponent > 0) {
      if (exponent % 2 == 1) {
        result = result.mul(x);
      }
      x = x.sqr();
      exponent /= 2;
    }
    return result.reduce();
  }

  /**
   * Raise a field point to a DBIG exponent.
   *
   * @param x the field point
   * @param exponent the DBIG exponent
   * @return the field point raised to the DBIG exponent and reduced
   */
  static FP2Immutable pow(FP2Immutable x, DBIG exponent) {
    FP2Immutable result = ONE;
    DBIGExtended exp = new DBIGExtended(exponent);
    exp.norm();
    while (!exp.iszilch()) {
      if (exp.parity() == 1) {
        result = result.mul(x);
      }
      x = x.sqr();
      exp.shr(1);
    }
    return result.reduce();
  }

  private final FP2 fp2;

  /**
   * Copy constructor
   *
   * @param fp2Immutable the FP2Immutable object to copy
   */
  FP2Immutable(FP2Immutable fp2Immutable) {
    this.fp2 = new FP2(fp2Immutable.fp2);
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
   * Constrcut from two BIG objects.
   *
   * @param big1 the first element
   * @param big2 the second element
   */
  FP2Immutable(BIG big1, BIG big2) {
    this.fp2 = new FP2(big1, big2);
  }

  /**
   * Construct from an integer.
   *
   * <p>The first element is set to the given integer, the second to zero.
   *
   * @param c integer to set the first element to
   */
  FP2Immutable(int c) {
    this.fp2 = new FP2(c);
  }

  /**
   * Construct from hexadecimal strings (prefixed "0x").
   *
   * @param hex1 hexadecimal representation of the first element
   * @param hex2 hexadecimal representation of the first element
   */
  FP2Immutable(String hex1, String hex2) {
    this.fp2 = new FP2(bigFromHex(hex1), bigFromHex(hex2));
  }

  FP2Immutable sqr() {
    FP2 result = new FP2(this.fp2);
    result.sqr();
    return new FP2Immutable(result);
  }

  FP2Immutable mul(FP2Immutable a) {
    FP2 result = new FP2(this.fp2);
    result.mul(a.fp2);
    return new FP2Immutable(result);
  }

  FP2Immutable add(FP2Immutable a) {
    FP2 result = new FP2(this.fp2);
    result.add(a.fp2);
    return new FP2Immutable(result);
  }

  FP2Immutable dbl() {
    FP2 result = new FP2(this.fp2);
    result.add(this.fp2);
    return new FP2Immutable(result);
  }

  FP2Immutable sub(FP2Immutable a) {
    FP2 result = new FP2(this.fp2);
    result.sub(a.fp2);
    return new FP2Immutable(result);
  }

  FP2Immutable neg() {
    FP2 result = new FP2(this.fp2);
    result.neg();
    return new FP2Immutable(result);
  }

  FP2Immutable reduce() {
    FP2 result = new FP2(this.fp2);
    result.reduce();
    return new FP2Immutable(result);
  }

  FP2Immutable inverse() {
    FP2 result = new FP2(this.fp2);
    result.inverse();
    return new FP2Immutable(result);
  }

  boolean iszilch() {
    return fp2.iszilch();
  }

  /**
   * Calculate the sign of the field element.
   *
   * @return -1 if x is the lexically larger of x and -1 * x, else returns 1
   */
  int sgn0() {
    // Threshold is (P - 1) // 2
    BIG thresh = new BIG(P).minus(new BIG(1));
    thresh.fshr(1);
    fp2.norm();
    int cmp = BIG.comp(fp2.getB(), thresh);
    if (cmp == 0) {
      cmp = BIG.comp(fp2.getA(), thresh);
    }
    return cmp == 0 ? 1 : -cmp;
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
    return this.reduce().toString().equals(other.reduce().toString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.reduce().toString());
  }
}
