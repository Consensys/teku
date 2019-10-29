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

package org.ethereum.beacon.ssz.fixtures;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;

/** Signature utilities Signature should be implemented using BLS */
public interface Sign {

  /** Sign the message */
  Signature sign(byte[] msg, BigInteger privateKey);

  /** Verifies whether signature is made by signer with publicKey */
  boolean verify(Signature signature, byte[] msg, BigInteger publicKey);

  /** Calculates public key from private */
  BigInteger privToPub(BigInteger privKey);

  /** Aggregates several signatures in one */
  Signature aggSigns(List<Signature> signatures);

  /** Aggregates public keys */
  BigInteger aggPubs(List<BigInteger> pubKeys);

  @SSZSerializable
  class Signature {
    public BigInteger r;
    public BigInteger s;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Signature signature = (Signature) o;
      return Objects.equals(r, signature.r) && Objects.equals(s, signature.s);
    }

    @Override
    public String toString() {
      return "Signature{" + "r=" + r + ", s=" + s + '}';
    }
  }
}
