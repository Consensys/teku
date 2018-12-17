/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.util.uint;

import org.junit.Assert;
import org.junit.Test;

public class UInt64Test {

  @Test
  public void addUnsigned() {
    //Test Basic Sum Accuracy
    // 0+0 = 0
    add(0, 0, "0");
    // 0+1 = 1
    add(0, 1, "1");
    // 1+0 = 1
    add(1, 0, "1");
    // 1+1 = 2
    add(1, 1, "2");

    //Test Edge Cases Around 32-bit and 64-bit Boundaries
    // 2^63-1 + 1 = 2^63 (instead of -2^63 as in signed arithmetic)
    add(Long.MAX_VALUE, 1, "9223372036854775808");
    // 2^63 + 2^63-1 = 2^64-1
    add(Long.MIN_VALUE, Long.MAX_VALUE, "18446744073709551615");
    // 2^64-1 + 1 = 0 (it is expected that an unsigned int will wrap)
    UInt64 uSum = UInt64.MAX_VALUE.plus(1);
    Assert.assertEquals("UInt64.MAX_VALUE + 1", "0", uSum.toString());
  }

  @Test
  public void subtractUnsigned() {
    //Test Basic Sum Accuracy
    // 0-0 = 0
    subtract(0, 0, "0");
    // 1-0 = 1
    subtract(1, 0, "1");
    // 1-1 = 0
    subtract(1, 1, "0");
    // 2-1 = 1
    subtract(2, 1, "1");

    //Test Edge Cases Around 32-bit and 64-bit Boundaries
    // 2^63 - 1 = 2^63-1
    subtract(Long.MIN_VALUE, 1, "9223372036854775807");
    // 0 - 1 = 2^64-1 (it is expected that an unsigned int will wrap)
    subtract(0, 1, "18446744073709551615");
    // 2^64-1 - 1 = 18446744073709551614 (2^64 - 2)
    UInt64 uDifference = UInt64.MAX_VALUE.minus(1);
    Assert.assertEquals("UInt64.MAX_VALUE - 1", "18446744073709551614", uDifference.toString());
  }

  @Test
  public void multiplyUnsigned() {
    //Test Basic Sum Accuracy
    // 0*0 = 0
    multiply(0, 0, "0");
    // 0*1 = 0
    multiply(0, 1, "0");
    // 1*0 = 0
    multiply(1, 0, "0");
    // 1*1 = 1
    multiply(1, 1, "1");
    // 2*2 = 4
    multiply(2, 2, "4");

    //Test Edge Cases Around 32-bit and 64-bit Boundaries
    // 2^63 * 1 = 2^63 (9223372036854775808)
    multiply(Long.MIN_VALUE, 1, "9223372036854775808");
    // 2^63 * 0 = 0
    multiply(Long.MIN_VALUE, 0, "0");
    // 2^63-1 * 2 = 2^64-2 (18446744073709551614)
    multiply(Long.MAX_VALUE, 2, "18446744073709551614");
    // 2^63-1 * 0 = 0
    multiply(Long.MAX_VALUE, 0, "0");
    // 2^64-1 * 1 = 2^64-1 (18446744073709551615)
    UInt64 uProduct = UInt64.MAX_VALUE.times(1);
    Assert.assertEquals("UInt64.MAX_VALUE * 1", "18446744073709551615", uProduct.toString());
    // 2^64-1 * 0 = 0
    UInt64 uProductZero = UInt64.MAX_VALUE.times(0);
    Assert.assertEquals("UInt64.MAX_VALUE * 0", "0", uProductZero.toString());
  }

  @Test
  public void divideUnsigned() {
    //Test Basic Quotient Accuracy
    // 2/1 = 2
    divide(2, 1, "2", false);
    // 3/1 = 3
    divide(3, 1, "3", false);
    // 4/2 = 2
    divide(4, 2, "2", false);

    //Test Remainder
    // 15/16 = 0 + 15/16
    divide(15, 16, "0", false);
    // 4/3 = 1 + 1/3
    divide(4, 3, "1", false);

    //Divide by 0
    // 0/0 (should throw IllegalArgumentException)
    divide(0, 0, "0", true);
    // 1/0 (should throw IllegalArgumentException)
    divide(1, 0, "0", true);
    // 0/1 (should not throw IllegalArgumentException)
    divide(0, 1, "0", false);

    //Test Edge Cases Around 32-bit and 64-bit Boundaries
    // 2^63 / 0 (should throw IllegalArgumentException)
    divide(Long.MIN_VALUE, 0, "0", true);
    // 2^63 / 2 = 2^62
    divide(Long.MIN_VALUE, 2, "4611686018427387904", false);
    // 2^64-1 / 1 = 2^64-1
    UInt64 uQuotient = UInt64.MAX_VALUE.dividedBy(1);
    Assert.assertEquals("UInt64.MAX_VALUE / 1", "18446744073709551615", uQuotient.toString());
    // 2^64-1 / 2 = 2^63-1
    UInt64 uQuotientMax = UInt64.MAX_VALUE.dividedBy(2);
    Assert.assertEquals("UInt64.MAX_VALUE / 1", "9223372036854775807", uQuotientMax.toString());
  }

  private void add(long augend, long addend, String expectedSum) {
    boolean thrown = false;

    try {
      final UInt64 uAugend = UInt64.valueOf(augend);

      UInt64 longSum = uAugend.plus(addend);
      Assert.assertEquals("Sum", expectedSum, Long.toUnsignedString(longSum.getValue()));
      Assert.assertEquals("UInt64 Sum", UInt64.valueOf(expectedSum), longSum);

      UInt64 uAddend = UInt64.valueOf(addend);
      UInt64 uintSum = uAugend.plus(uAddend);
      Assert.assertEquals("Sum", expectedSum, Long.toUnsignedString(uintSum.getValue()));
      Assert.assertEquals("UInt64 Sum", UInt64.valueOf(expectedSum), uintSum);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Addition should not have thrown an exception.", false, thrown);
  }

  private void subtract(long minuend, long subtrahend, String expectedDifference) {
    boolean thrown = false;

    try {
      final UInt64 uMinuend = UInt64.valueOf(minuend);

      UInt64 longDifference = uMinuend.minus(subtrahend);
      Assert.assertEquals("Difference", expectedDifference, Long.toUnsignedString(longDifference.getValue()));
      Assert.assertEquals("UInt64 Difference", UInt64.valueOf(expectedDifference), longDifference);

      UInt64 uSubtrahend = UInt64.valueOf(subtrahend);
      UInt64 uintDifference = uMinuend.minus(uSubtrahend);
      Assert.assertEquals("Difference", expectedDifference, Long.toUnsignedString(uintDifference.getValue()));
      Assert.assertEquals("UInt64 Difference", UInt64.valueOf(expectedDifference), uintDifference);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Subtraction should not have thrown an exception.", false, thrown);
  }

  private void multiply(long multiplicand, long multiplier, String expectedProduct) {
    boolean thrown = false;

    try {
      final UInt64 uMultiplicand = UInt64.valueOf(multiplicand);

      UInt64 longProduct = uMultiplicand.times(multiplier);
      Assert.assertEquals("Product", expectedProduct, Long.toUnsignedString(longProduct.getValue()));
      Assert.assertEquals("UInt64 Product", UInt64.valueOf(expectedProduct), longProduct);

      UInt64 uMultiplier = UInt64.valueOf(multiplier);
      UInt64 uintProduct = uMultiplicand.times(uMultiplier);
      Assert.assertEquals("Product", expectedProduct, Long.toUnsignedString(uintProduct.getValue()));
      Assert.assertEquals("UInt64 Product", UInt64.valueOf(expectedProduct), uintProduct);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Multiplication should not have thrown an exception.", false, thrown);
  }

  private void divide(long dividend, long divisor, String expectedQuotient, boolean shouldThrow) {
    if(shouldThrow) {
      divideExpectException(dividend, divisor, expectedQuotient);
    } else {
      divideExpectResult(dividend, divisor, expectedQuotient);
    }
  }

  private void divideExpectException(long dividend, long divisor, String expectedQuotient) {
    try {
      final UInt64 uDividend = UInt64.valueOf(dividend);

      UInt64 longQuotient = uDividend.dividedBy(divisor);
      UInt64 uDivisor = UInt64.valueOf(divisor);
      UInt64 uintQuotient = uDividend.dividedBy(uDivisor);

      Assert.fail("Exception was expected but not thrown.");
    } catch (Exception e) {
      Assert.assertTrue("Division operation was expected to throw an exception of type IllegalArgumentException.", e instanceof IllegalArgumentException);
      Assert.assertTrue("Exception message was not correct.", e.getMessage().equals("Argument 'divisor' is 0."));
    }
  }

  private void divideExpectResult(long dividend, long divisor, String expectedQuotient) {
    boolean thrown = false;

    try {
      final UInt64 uDividend = UInt64.valueOf(dividend);

      UInt64 longQuotient = uDividend.dividedBy(divisor);
      Assert.assertEquals("Quotient", expectedQuotient, Long.toUnsignedString(longQuotient.getValue()));
      Assert.assertEquals("UInt64 Division Result", UInt64.valueOf(expectedQuotient), longQuotient);

      UInt64 uDivisor = UInt64.valueOf(divisor);
      UInt64 uintQuotient = uDividend.dividedBy(uDivisor);
      Assert.assertEquals("Quotient", expectedQuotient, Long.toUnsignedString(uintQuotient.getValue()));
      Assert.assertEquals("UInt64 Division Result", UInt64.valueOf(expectedQuotient), uintQuotient);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Division should not have thrown an exception.", false, thrown);
  }
}
