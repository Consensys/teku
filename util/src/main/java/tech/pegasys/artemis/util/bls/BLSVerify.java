package tech.pegasys.artemis.util.bls;

import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.uint.UInt381;
import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

public class BLSVerify {

  private int g_x = 3685416753713387016781088315183077757961620795782546409894578378688607592378376318836054947676345821548104185464507;
  private int g_y = 1339506544944476473020471379941921221584933875938349620426543736416511423956333506472724655353366534992391756441569;
  g = Fq2(g_x, g_y);

  private static UInt381 G2_cofactor =
      305502333931268344200999753193121504214466019254188142667664032982267604182971884026507427359259977847832272839041616661285803823378372096355777062779109;
  private static UInt381 q =
      4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787;

  private static double Fq2_order = Math.pow(q.getValue(), 2) - 1;
  private static int root_of_unity = Math.pow(Fq2([1,1]), ((Fq2_order * k) / 8));
  private static int[] eighth_roots_of_unity = new int[]{root_of_unity, root_of_unity, root_of_unity, root_of_unity,
      root_of_unity, root_of_unity, root_of_unity, root_of_unity};


  public boolean bls_verify(UInt384 pubkey, Bytes32 message, UInt384[] signature, UInt64 domain) {
    // Verify that pubkey is a valid G1 point
    boolean valid_g1 = valid_g1(pubkey);

    // Verify that signature is a valid G2 point
    boolean valid_g2 = valid_g2(signature);

    return e(pubkey, hash_to_G2(message, domain)) == e(g, signature);
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

    return false;
  }

  /**
   *
   * @param message
   * @param domain
   * @return
   */
  private UInt384[] hash_to_G2(Bytes32 message, UInt64 domain) {
    // Initial candidate x coordinate
    int x_re = int.from_bytes(hash(new bytes(domain) + b'\x01' + message), 'big');
    int x_im = int.from_bytes(hash(new bytes(domain) + b'\x02' + message), 'big');
    int x_coordinate = Fq2([x_re, x_im]);  // x = x_re + i * x_im;

    //  Test candidate y coordinates until a one is found
    while (true) {
      double y_coordinate_squared = Math.pow(x_coordinate, 3) + Fq2([4, 4]); // The curve is y^2 = x^3 + 4(i + 1);
      int y_coordinate = modular_squareroot(y_coordinate_squared);
      if (y_coordinate != -1) { // Check if quadratic residue found
        return multiply_in_G2((x_coordinate, y_coordinate), G2_cofactor);
      }
      x_coordinate += Fq2([1, 0]);  // Add 1 and try again
    }
  }

  private int modular_squareroot(int value) {
    double candidate_squareroot = Math.pow(value, (Fq2_order + 8) / 16);
    double check = Math.pow(candidate_squareroot, 2) / value;

    boolean check_in = false;
    for (int i = 0; i < eighth_roots_of_unity.length; i += 2) {
      if (check == eighth_roots_of_unity[i]) {
        check_in = true;
        break;
      }
    }

    if (check_in) {
      int x1 = candidate_squareroot / eighth_roots_of_unity[eighth_roots_of_unity.index(check) / 2];
      int x2 = -x1;
      if ((x1.coeffs[1].n, x1.coeffs[0].n) > (x2.coeffs[1].n, x2.coeffs[0].n)) ? x1 : x2;
    }
    return -1;
  }


}
