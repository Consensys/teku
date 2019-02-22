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

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.Signature;

public final class BLSSignature {

  /** Signs a message */
  public static BLSSignature sign(BLSKeyPair keyPair, Bytes message, long domain) {
    return new BLSSignature(
        BLS12381
            .sign(new KeyPair(keyPair.secretKey(), keyPair.publicKey()), message, domain)
            .signature());
  }

  /**
   * Creates a random, but valid, signature
   *
   * @return the signature
   */
  public static BLSSignature random() {
    return new BLSSignature(Signature.random());
  }

  /**
   * Creates an empty signature (all zero bytes)
   *
   * @return the signature
   */
  public static BLSSignature empty() {
    return new BLSSignature(Signature.decode(Bytes.of(new byte[192])));
  }

  public static BLSSignature fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes, reader -> new BLSSignature(Signature.decodeCompressed(reader.readBytes())));
  }

  private Signature signature;

  public BLSSignature(Signature signature) {
    this.signature = signature;
  }

  /**
   * Returns the SSZ serialisation of the <em>compressed</em> form of the signature
   *
   * @return the serialisation of the compressed form of the signature.
   */
  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(signature.encodeCompressed());
        });
  }

  public Signature getSignature() {
    return signature;
  }

  @Override
  public String toString() {
    return signature.toString();
  }

  @Override
  public int hashCode() {
    return signature.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BLSSignature)) {
      return false;
    }
    BLSSignature other = (BLSSignature) obj;
    return signature.equals(other.signature);
  }
}
