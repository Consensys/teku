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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BLSVerify {

  /**
   * The bls_verify() function as defined in the Eth2 specification
   *
   * @param pubkey the compressed public key
   * @param messageHash the message digest signed
   * @param signature the signature
   * @param domain the domain parameter defined by the spec
   * @return true if the signature is valid over these parameters, false if not
   */
  public static boolean bls_verify(
      BLSPublicKey pubkey, Bytes32 messageHash, BLSSignature signature, Bytes domain) {
    try {
      return signature.checkSignature(pubkey, Bytes.wrap(messageHash), domain);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
