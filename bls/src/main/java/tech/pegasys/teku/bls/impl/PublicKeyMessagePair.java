/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;

/**
 * Tuple of {@link PublicKey} and message {@link Bytes} used for batch verification
 *
 * @see Signature#verify(List)
 */
public class PublicKeyMessagePair {
  private final PublicKey publicKey;
  private final Bytes message;

  public PublicKeyMessagePair(PublicKey publicKey, Bytes message) {
    this.publicKey = publicKey;
    this.message = message;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public Bytes getMessage() {
    return message;
  }
}
