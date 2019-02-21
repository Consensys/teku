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

package tech.pegasys.artemis.util.bitwise;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.Random;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BouncyCastleExtension.class)
class BitwiseOpstTest {

  @Test
  void basicOr() {
    UnsignedLong binary1 = UnsignedLong.ONE;
    UnsignedLong binary10 = UnsignedLong.valueOf(2);
    UnsignedLong binary11 = UnsignedLong.valueOf(3);
    UnsignedLong result = BitwiseOps.or(binary1, binary10);

    assertThat(result).isEqualTo(binary11);
  }

  @Test
  void randomOr() {
    // Define random integers
    Random rand = new Random();
    int rand_int1;
    int rand_int2;
    int integer_bitwise_or;
    UnsignedLong rand_unsigned1;
    UnsignedLong rand_unsigned2;
    UnsignedLong unsigned_bitwise_or;

    // Repeat random test SECURITY_THRESHOLD times
    int SECURITY_THRESHOLD = 100;
    for (int i = 0; i < SECURITY_THRESHOLD; i++) {
      rand_int1 = rand.nextInt(Integer.MAX_VALUE);
      rand_int2 = rand.nextInt(Integer.MAX_VALUE);
      integer_bitwise_or = rand_int1 | rand_int2;

      rand_unsigned1 = UnsignedLong.valueOf(rand_int1);
      rand_unsigned2 = UnsignedLong.valueOf(rand_int2);
      unsigned_bitwise_or = BitwiseOps.or(rand_unsigned1, rand_unsigned2);
      assertThat(UnsignedLong.valueOf(integer_bitwise_or)).isEqualTo(unsigned_bitwise_or);
    }
  }

  @Test
  void bitwiseAnd() {
    UnsignedLong binary1 = UnsignedLong.ONE;
    UnsignedLong binary10 = UnsignedLong.valueOf(2);
    UnsignedLong binary0 = UnsignedLong.valueOf(0);
    UnsignedLong result = BitwiseOps.and(binary1, binary10);

    assertThat(result).isEqualTo(binary0);
  }

  @Test
  void bitwiseLeftShift() {
    // Define random variables
    Random rand = new Random();
    long rand_long;
    UnsignedLong rand_unsigned;

    long long_bitwise_left;
    UnsignedLong unsigned_bitwise_left;

    int shift_num;

    // Repeat random test SECURITY_THRESHOLD times
    int SECURITY_THRESHOLD = 100;
    for (int i = 0; i < SECURITY_THRESHOLD; i++) {

      rand_long = rand.nextLong();
      if (rand_long < 0) rand_long *= rand_long;
      rand_unsigned = UnsignedLong.fromLongBits(rand_long);

      shift_num = rand.nextInt(10);

      long_bitwise_left = rand_long << shift_num;
      unsigned_bitwise_left = BitwiseOps.leftShift(rand_unsigned, shift_num);

      assertThat(Long.toString(long_bitwise_left, 2))
          .isEqualTo(Long.toString(unsigned_bitwise_left.longValue(), 2));
    }
  }

  @Test
  void bitwiseRightShift() {
    // Define random variables
    Random rand = new Random();
    long rand_long;
    UnsignedLong rand_unsigned;

    long long_bitwise_right;
    UnsignedLong unsigned_bitwise_right;

    int shift_num;

    // Repeat random test SECURITY_THRESHOLD times
    int SECURITY_THRESHOLD = 100;
    for (int i = 0; i < SECURITY_THRESHOLD; i++) {

      rand_long = rand.nextLong();
      if (rand_long < 0) rand_long *= rand_long;
      rand_unsigned = UnsignedLong.fromLongBits(rand_long);

      shift_num = rand.nextInt(10);

      long_bitwise_right = rand_long >> shift_num;
      unsigned_bitwise_right = BitwiseOps.rightShift(rand_unsigned, shift_num);

      assertThat(Long.toString(long_bitwise_right, 2))
          .isEqualTo(Long.toString(unsigned_bitwise_right.longValue(), 2));
    }
  }
}
