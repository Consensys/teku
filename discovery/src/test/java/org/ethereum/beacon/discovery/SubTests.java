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

package org.ethereum.beacon.discovery;

import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

/**
 * Secondary tests not directly related to discovery but clarifying functions used somewhere in
 * discovery routines
 */
public class SubTests {
  /**
   * Tests BigInteger to byte[]. Take a look at {@link
   * Utils#extractBytesFromUnsignedBigInt(BigInteger)} for understanding the issue.
   */
  @Test
  public void testPubKeyBadPrefix() {
    Bytes privKey =
        Bytes.fromHexString("0xade78b68f25611ea57532f86bf01da909cc463465ed9efce9395403ff7fc99b5");
    ECKeyPair badKey = ECKeyPair.create(privKey.toArray());
    byte[] pubKey = Utils.extractBytesFromUnsignedBigInt(badKey.getPublicKey());
    Assertions.assertEquals(64, pubKey.length);
  }
}
