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

package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

/**
 * BLS Key Store implementation EIP-2335
 *
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2335.md">EIP-2335</a>
 */
public class KeyStore {
  private final KeyStoreData keyStoreData;
  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new KeyStoreBytesModule());

  public KeyStore(final KeyStoreData keyStoreData) {
    this.keyStoreData = keyStoreData;
  }

  public static KeyStore loadFromJson(final String json) throws Exception {
    final KeyStoreData keyStoreData = objectMapper.readValue(json, KeyStoreData.class);
    return new KeyStore(keyStoreData);
  }

  public static KeyStore loadFromFile(final File keystoreFile) throws Exception {
    final KeyStoreData keyStoreData = objectMapper.readValue(keystoreFile, KeyStoreData.class);
    return new KeyStore(keyStoreData);
  }

  public KeyStoreData getKeyStoreData() {
    return keyStoreData;
  }

  public String toJson() throws Exception {
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(keyStoreData);
  }
}
