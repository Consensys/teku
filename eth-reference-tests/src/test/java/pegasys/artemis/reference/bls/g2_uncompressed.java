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

package pegasys.artemis.reference.bls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.mikuli.G2Point;

class g2_uncompressed extends TestSuite {

  @ParameterizedTest(name = "{index}. message hash to G2 uncompressed {0} -> {1}")
  @MethodSource("readMessageHashG2Uncompressed")
  void testMessageHashToG2Uncompressed(
      LinkedHashMap<String, String> input, ArrayList<ArrayList<String>> output) {

    Bytes domain = Bytes.fromHexString(input.get("domain"));
    Bytes message = Bytes.fromHexString(input.get("message"));

    G2Point referencePoint = makePoint(output);

    assertEquals(referencePoint, G2Point.hashToG2(message, domain));
  }

  @MustBeClosed
  private static Stream<Arguments> readMessageHashG2Uncompressed() throws IOException {
    return findBLSTests("**/g2_uncompressed.yaml", "test_cases");
  }

  /**
   * Utility for converting uncompressed test case data to a point
   *
   * <p>The test case data is not in standard form (Z = 1). This routine converts the input to a
   * point and applies the affine transformation. This routine is for uncompressed input.
   *
   * @param coords an array of strings {xRe, xIm, yRe, yIm, zRe, zIm}
   * @return the point corresponding to the input
   */
  private static G2Point makePoint(ArrayList<ArrayList<String>> coords) {
    BIG xRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(0).get(0))).toArray());
    BIG xIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(0).get(1))).toArray());
    BIG yRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(1).get(0))).toArray());
    BIG yIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(1).get(1))).toArray());
    BIG zRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(2).get(0))).toArray());
    BIG zIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(2).get(1))).toArray());

    FP2 x = new FP2(xRe, xIm);
    FP2 y = new FP2(yRe, yIm);
    FP2 z = new FP2(zRe, zIm);

    // Normalise the point (affine transformation) so that Z = 1
    z.inverse();
    x.mul(z);
    x.reduce();
    y.mul(z);
    y.reduce();

    return new G2Point(new ECP2(x, y));
  }
}
