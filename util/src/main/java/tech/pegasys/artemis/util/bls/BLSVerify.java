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

package tech.pegasys.artemis.util.bls;

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt381;
import tech.pegasys.artemis.util.uint.UInt384;

import java.util.ArrayList;

public class BLSVerify {

  private int g_x =
      3685416753713387016781088315183077757961620795782546409894578378688607592378376318836054947676345821548104185464507;
  private int g_y =
      1339506544944476473020471379941921221584933875938349620426543736416511423956333506472724655353366534992391756441569;
  private int g = Fq2(new int[]{g_x, g_y});

  private static UInt381 G2_cofactor =
      305502333931268344200999753193121504214466019254188142667664032982267604182971884026507427359259977847832272839041616661285803823378372096355777062779109;
  private static UInt381 q =
      4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787;

  private static int Fq2_order = (int) Math.pow(q.getValue(), 2) - 1;
  private static ArrayList<Integer> eighth_roots_of_unity = calculate_root_of_unity();

  /**
   *
   * @return
   */
  private static ArrayList<Integer> calculate_root_of_unity() {
    ArrayList<Integer> eighth_roots_of_unity = new ArrayList<>(8);
    for (int i = 0; i < 8; i++) {
      eighth_roots_of_unity.add((int) Math.pow(Fq2(new int[]{1, 1}), (Fq2_order * i) / 8.0));
    }
    return eighth_roots_of_unity;
  }

  /**
   *
   * @param point
   * @return
   */
  private static int Fq2(int[] point) {
    return 1;
  }

  /**
   *
   * @param pubkey
   * @param message
   * @param signature
   * @param domain
   * @return
   */
  public boolean bls_verify(UInt384 pubkey, Bytes32 message, UInt384[] signature, int domain) {
    // Verify that pubkey is a valid G1 point
    assert valid_g1(pubkey);

    // Verify that signature is a valid G2 point
    assert valid_g2(signature);

    return e(pubkey, hash_to_G2(message, domain)) == e(g, signature);
  }

  /**
   * Elliptic curve function
   * @param pubkey
   * @param message
   * @return
   */
  private UInt384[] e(UInt384 pubkey, UInt384[] message) {
    // TODO
    return new UInt384[]{};
  }

  /**
   *
   * @param pubkeys
   * @param messages
   * @param signature
   * @param domain
   * @return
   */
  public boolean bls_verify_multiple(UInt384[] pubkeys, Bytes32[] messages, UInt384[] signature, int domain) {
    // Verify that each pubkey in pubkeys is a valid G1 point
    for (UInt384 pubkey : pubkeys) {
      if (!valid_g1(pubkey)) return false;
    }

    // Verify that signature is a valid G2 point
    assert valid_g2(signature);

    // Verify that len(pubkeys) equals len(messages) and denote the length L.
    assert pubkeys.length == messages.length;

    // Verify that e(pubkeys[0], hash_to_G2(messages[0], domain)) * ... *
    // e(pubkeys[L-1], hash_to_G2(messages[L-1], domain)) == e(g, signature)
    for (int i = 0 ; i < pubkeys.length; i++) {
      if (e(pubkeys[i], hash_to_G2(messages[i], domain)) != e(g, signature)) return false;
    }
    return true;
  }


  /**
   *
   * @param z
   * @return
   */
  private boolean valid_g1(UInt384 z) {
    long z_ = z.getValue();

    UInt381 x = UInt381.valueOf(z_ % (long) Math.pow(2, 381));
    boolean a_flag = z_ % Math.pow(2, 382) / Math.pow(2, 381) == 1;
    boolean b_flag = z_ % Math.pow(2, 383) / Math.pow(2, 382) == 1;
    boolean c_flag = z_ % Math.pow(2, 384) / Math.pow(2, 383) == 1;
    // Respecting bit ordering, z is decomposed as (c_flag, b_flag, a_flag, x).

    assert x.getValue() < q.getValue();
    assert c_flag;
    if (b_flag) {
      assert a_flag && x == UInt381.ZERO;
      // z represents the point at infinity
    } else {
      assert (y * 2) / q == a_flag;
      // z represents the point (x, y) where
      // y is the valid coordinate such that (y * 2) / q == a_flag
    }
    return true;
  }

  /**
   * A point in G2 is represented as a pair of 384-bit integers (z1, z2).
   * @param z1z2
   * @return
   */
  private boolean valid_g2(UInt384[] z1z2) {
    // We decompose z1 as above into x1, a_flag1, b_flag1, c_flag1 and z2 into x2, a_flag2, b_flag2, c_flag2.
    long z1 = z1z2[0].getValue();
    long z2 = z1z2[1].getValue();

    UInt381 x1 = UInt381.valueOf(z2 % (long) Math.pow(2, 381));
    boolean a_flag1 = z1 % Math.pow(2, 382) / Math.pow(2, 381) == 1;
    boolean b_flag1 = z1 % Math.pow(2, 383) / Math.pow(2, 382) == 1;
    boolean c_flag1 = z1 % Math.pow(2, 384) / Math.pow(2, 383) == 1;

    UInt381 x2 = UInt381.valueOf(z2 % (long) Math.pow(2, 381));
    boolean a_flag2 = z2 % Math.pow(2, 382) / Math.pow(2, 381) == 1;
    boolean b_flag2 = z2 % Math.pow(2, 383) / Math.pow(2, 382) == 1;
    boolean c_flag2 = z2 % Math.pow(2, 384) / Math.pow(2, 383) == 1;

    assert x1.getValue() < q.getValue() && x2.getValue() < q.getValue();
    assert a_flag2 == b_flag2 && b_flag2 == c_flag2 && !c_flag2 && c_flag1;

    if (b_flag1) {
      assert !a_flag1 && x1.getValue() == x2.getValue() && x2 == UInt381.ZERO;
      // (z1, z2) represents the point at infinity
    } else {
      assert z1 == x1 * i + x2 && z2 == y;
      // then (z1, z2) represents the point (x1 * i + x2, y)
      // where y is the valid coordinate such that the imaginary part y_im of y satisfies
      assert y_im * 2 / q == a_flag1;
    }

    return true;
  }

  /**
   *
   * @param message
   * @param domain
   * @return
   */
  private UInt384[] hash_to_G2(Bytes32 message, int domain) {
    // Initial candidate x coordinate
    BytesValue x_re_arg = intToBytes32(domain) + (byte) 1 + message;
    BytesValue x_im_arg = intToBytes32(domain) + (byte) 2 + message;
    int x_re = hashToInt(Hash.hash(x_re_arg));
    int x_im = hashToInt(Hash.hash(x_im_arg));
    int x_coordinate = Fq2(new int[]{x_re, x_im});  // x = x_re + i * x_im;

    //  Test candidate y coordinates until a one is found
    while (true) {
      int y_coordinate_squared = (int) Math.pow(x_coordinate, 3) +
          Fq2(new int[]{4 ,4}); // The curve is y^2 = x^3 + 4(i + 1);
      int y_coordinate = modular_squareroot(y_coordinate_squared);
      if (y_coordinate != -1) { // Check if quadratic residue found
        return multiply_in_G2(new int[]{x_coordinate, y_coordinate}, G2_cofactor);
      }
      x_coordinate += Fq2(new int[]{1, 0});  // Add 1 and try again
    }
  }

  /**
   * Convert integer to Bytes32.
   * @param num
   * @return
   */
  private Bytes32 intToBytes32(int num) {
    // TODO
    return Bytes32.TRUE;
  }

  /**
   * Convert hash to int value.
   * @param hash
   * @return
   */
  private int hashToInt(Hash hash) {
    // TODO
    return 0;
  }

  /**
   *
   * @param point
   * @param cofactor
   * @return
   */
  private UInt384[] multiply_in_G2(int[] point, UInt381 cofactor) {
    // TODO
    return new UInt384[]{};
  }

  /**
   * If there are two solutions the one with higher imaginary component is favored; if both solutions have equal
   * imaginary component the one with higher real component is favored.
   * @param value
   * @return
   */
  private int modular_squareroot(int value) {
    double candidate_squareroot = Math.pow(value, (Fq2_order + 8) / 16.0);
    int check = (int) Math.pow(candidate_squareroot, 2) / value;

    boolean check_in = false;
    for (int i = 0; i < eighth_roots_of_unity.size(); i += 2) {
      if (check == eighth_roots_of_unity.get(i)) {
        check_in = true;
        break;
      }
    }

    if (check_in) {
      int x1 = (int) candidate_squareroot / eighth_roots_of_unity.get(eighth_roots_of_unity.indexOf(check) / 2);
      int x2 = -x1;
      if ((x1.coeffs[1].n, x1.coeffs[0].n) > (x2.coeffs[1].n, x2.coeffs[0].n)) ? return x1 : return x2;
    }
    return -1;
  }

}
