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

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.util.alogger.ALogger;

public class BLSVerify {
  private static final ALogger LOG = new ALogger(BLSVerify.class.getName());
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
      BLSPublicKey pubkey, Bytes32 messageHash, BLSSignature signature, UnsignedLong domain) {
    try {
      return signature.checkSignature(pubkey, Bytes.wrap(messageHash), domain.longValue());
    } catch (BLSException e) {
      LOG.log(Level.WARN, e.toString());
      return false;
    }
  }

  /**
   * *
   *
   * <p>The bls_verify_multiple() function as defined in the Eth2 specification
   *
   * @param pubkeys a list of compressed public keys
   * @param messageHashes a list of the same number of messages
   * @param aggregateSignature the single signature over these public keys and messages
   * @param domain the domain parameter defined by the spec
   * @return true if the signature is valid over these parameters, false if not
   */
  public static boolean bls_verify_multiple(
      List<BLSPublicKey> pubkeys,
      List<Bytes32> messageHashes,
      BLSSignature aggregateSignature,
      UnsignedLong domain) {
    try {
      List<Bytes> messageHashesAsBytes =
          messageHashes.stream().map(x -> Bytes.wrap(x)).collect(Collectors.toList());
      return aggregateSignature.checkSignature(pubkeys, messageHashesAsBytes, domain.longValue());
    } catch (BLSException e) {
      LOG.log(Level.WARN, e.toString());
      return false;
    }
  }
}
