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
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.mikuli.Signature;

public final class BLSEmptySignature extends BLSSignature {

  public BLSEmptySignature() {}

  /**
   * Serialise the empty signature
   *
   * <p>The empty signature must serialise as 96 * zero bytes as defined in the Eth specification.
   * No valid signature can do this.
   *
   * @return the serialisation of the empty signature
   */
  @Override
  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(Bytes.wrap(new byte[96]));
        });
  }

  /**
   * Calling the checkSignature method on an empty signature indicates a logic error somewhere, so
   * we throw a runtime exception.
   *
   * @param publicKey not used
   * @param message not used
   * @param domain not used
   * @throws RuntimeException when called
   */
  @Override
  boolean checkSignature(Bytes48 publicKey, Bytes message, long domain) {
    throw new RuntimeException("The checkSignature method was called on an empty signature.");
  }

  /**
   * Calling the getSignature method on an empty signature indicates a logic error somewhere, so we
   * throw a runtime exception.
   */
  @Override
  public Signature getSignature() {
    throw new RuntimeException("The getSignature method was called on an empty signature.");
  }

  @Override
  public String toString() {
    return "Empty Signature";
  }

  @Override
  public int hashCode() {
    return 42;
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    return (obj instanceof BLSEmptySignature);
  }
}
