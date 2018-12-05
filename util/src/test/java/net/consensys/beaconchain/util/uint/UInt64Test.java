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

package net.consensys.beaconchain.util.uint;

import org.junit.Assert;
import org.junit.Test;

public class UInt64Test {

  @Test
  public void addUnsigned() {
    //Test Basic Sum Accuracy
    // 0+0 = 0
    add(0, 0, 0, true);
    add(0, 0, 0, false);

    // 0+1 = 1
    add(0, 1, 1, true);
    add(0, 1, 1, false);

    // 1+0 = 1
    add(1, 0, 1, true);
    add(1, 0, 1, false);

    // 1+1 = 2
    add(1, 1, 2, true);
    add(1, 1, 2, false);

    //TODO Add more tests closer to unsigned boundaries.
  }

  @Test
  public void subtractUnsigned() {
    //Test Basic Sum Accuracy
    // 0-0 = 0
    subtract(0, 0, 0, true);
    subtract(0, 0, 0, false);

    // 1-0 = 1
    subtract(1, 0, 1, true);
    subtract(1, 0, 1, false);

    // 1-1 = 0
    subtract(1, 1, 0, true);
    subtract(1, 1, 0, false);

    // 2-1 = 1
    subtract(2, 1, 1, true);
    subtract(2, 1, 1, false);

    //TODO Add more tests closer to unsigned boundaries.
    // 0-1 = ?
    //subtract(1, 1, 2, true);
    //subtract(1, 1, 2, false);
  }

  @Test
  public void multiplyUnsigned() {
    //Test Basic Sum Accuracy
    // 0*0 = 0
    multiply(0, 0, 0, true);
    multiply(0, 0, 0, false);

    // 0*1 = 0
    multiply(0, 1, 0, true);
    multiply(0, 1, 0, false);

    // 1*0 = 0
    multiply(1, 0, 0, true);
    multiply(1, 0, 0, false);

    // 1*1 = 1
    multiply(1, 1, 1, true);
    multiply(1, 1, 1, false);

    // 2*2 = 4
    multiply(2, 2, 4, true);
    multiply(2, 2, 4, false);

    //TODO Add more tests closer to unsigned boundaries.
  }

  @Test
  public void divideUnsigned() {
    //Test Basic Quotient Accuracy
    // 2/1 = 2
    divide(2, 1, 2, false, false);
    divide(2, 1, 2, false, false);
    // 3/1 = 3
    divide(3, 1, 3, true, false);
    divide(3, 1, 3, false, false);
    // 4/2 = 2
    divide(4, 2, 2, true, false);
    divide(4, 2, 2, false, false);

    //Test Remainder
    // 15/16 = 0 + 15/16
    divide(15, 16, 0, true, false);
    divide(15, 16, 0, false, false);
    // 4/3 = 1 + 1/3
    divide(4, 3, 1, true, false);
    divide(4, 3, 1, false, false);

    //Divide by 0
    // 0/0 (should throw IllegalArgumentException)
    divide(0, 0, 0, true, true);
    divide(0, 0, 0, false, true);
    // 1/0 (should throw IllegalArgumentException)
    divide(1, 0, 0, true, true);
    divide(1, 0, 0, false, true);
    // 0/1 (should not throw IllegalArgumentException)
    divide(0, 1, 0, true, false);
    divide(0, 1, 0, false, false);

    //TODO Add more tests closer to unsigned boundaries.
  }

  private void add(long augend, long addend, long expectedSum, boolean treatSecondArgumentAsPrimitive) {
    boolean thrown = false;

    try {
      UInt64 uSum = UInt64.valueOf(augend);
      if(treatSecondArgumentAsPrimitive) {
        uSum.plus(addend);
      } else {
        UInt64 uAddend = UInt64.valueOf(addend);
        uSum.plus(uAddend);
      }
      Assert.assertEquals("Sum", expectedSum, uSum.getValue());
      Assert.assertEquals("UInt64 Sum", UInt64.valueOf(expectedSum), uSum);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Addition should not have thrown an exception.", false, thrown);
  }

  private void subtract(long minuend, long subtrahend, long expectedDifference, boolean treatSecondArgumentAsPrimitive) {
    boolean thrown = false;

    try {
      UInt64 uDifference = UInt64.valueOf(minuend);
      if(treatSecondArgumentAsPrimitive) {
        uDifference.minus(subtrahend);
      } else {
        UInt64 uSubtrahend = UInt64.valueOf(subtrahend);
        uDifference.minus(uSubtrahend);
      }
      Assert.assertEquals("Difference", expectedDifference, uDifference.getValue());
      Assert.assertEquals("UInt64 Difference", UInt64.valueOf(expectedDifference), uDifference);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Subtraction should not have thrown an exception.", false, thrown);
  }

  private void multiply(long multiplicand, long multiplier, long expectedProduct, boolean treatSecondArgumentAsPrimitive) {
    boolean thrown = false;

    try {
      UInt64 uProduct = UInt64.valueOf(multiplicand);
      if(treatSecondArgumentAsPrimitive) {
        uProduct.times(multiplier);
      } else {
        UInt64 uMultiplier = UInt64.valueOf(multiplier);
        uProduct.times(uMultiplier);
      }
      Assert.assertEquals("Product", expectedProduct, uProduct.getValue());
      Assert.assertEquals("UInt64 Product", UInt64.valueOf(expectedProduct), uProduct);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Multiplication should not have thrown an exception.", false, thrown);
  }

  private void divide(long dividend, long divisor, long expectedQuotient, boolean treatSecondArgumentAsPrimitive, boolean shouldThrow) {
    if(shouldThrow) {
      divideExpectException(dividend, divisor, expectedQuotient, treatSecondArgumentAsPrimitive);
    } else {
      divideExpectResult(dividend, divisor, expectedQuotient, treatSecondArgumentAsPrimitive);
    }
  }

  private void divideExpectException(long dividend, long divisor, long expectedQuotient, boolean treatSecondArgumentAsPrimitive) {
    try {
      UInt64 uQuotient = UInt64.valueOf(dividend);
      if(treatSecondArgumentAsPrimitive) {
        uQuotient.dividedBy(divisor);
      } else {
        UInt64 uDivisor = UInt64.valueOf(divisor);
        uQuotient.dividedBy(uDivisor);
      }
      Assert.fail("Exception was expected but not thrown.");
    } catch (Exception e) {
      Assert.assertTrue("Division operation was expected to throw an exception of type IllegalArgumentException.", e instanceof IllegalArgumentException);
      Assert.assertTrue("Exception message was not correct.", e.getMessage().equals("Argument 'divisor' is 0."));
    }
  }

  private void divideExpectResult(long dividend, long divisor, long expectedQuotient, boolean treatSecondArgumentAsPrimitive) {
    boolean thrown = false;

    try {
      UInt64 uQuotient = UInt64.valueOf(dividend);
      if(treatSecondArgumentAsPrimitive) {
        uQuotient.dividedBy(divisor);
      } else {
        UInt64 uDivisor = UInt64.valueOf(divisor);
        uQuotient.dividedBy(uDivisor);
      }
      Assert.assertEquals("Quotient", expectedQuotient, uQuotient.getValue());
      Assert.assertEquals("UInt64 Division Result", UInt64.valueOf(expectedQuotient), uQuotient);
    } catch (Exception e) {
      thrown = true;
    }

    Assert.assertEquals("Division should not have thrown an exception.", false, thrown);
  }
}
