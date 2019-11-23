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

package org.ethereum.beacon.discovery.schema;

/**
 * Fields of Ethereum Node Record V4 as defined by <a
 * href="https://eips.ethereum.org/EIPS/eip-778">https://eips.ethereum.org/EIPS/eip-778</a>
 */
public interface EnrFieldV4 extends EnrField {
  // Compressed secp256k1 public key, 33 bytes
  String PKEY_SECP256K1 = "secp256k1";
}
