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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.milagro.amcl.BLS381.DBIG;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class DBIGExtendedTest {

  @Test
  void isOdd1() {
    DBIGExtended a = new DBIGExtended(new DBIG(1));
    assertTrue(a.isOdd());
  }

  @Test
  void isOdd2() {
    DBIGExtended a = new DBIGExtended(new DBIG(2));
    assertFalse(a.isOdd());
  }

  @Test
  void fromBytesRoundTrip() {
    String expected =
        "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0"
            + "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0";
    String actual = new DBIGExtended(Bytes.fromHexString(expected).toArray()).toString();
    assertEquals(expected, actual);
  }
}
