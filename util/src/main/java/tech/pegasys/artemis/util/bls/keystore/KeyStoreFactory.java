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

public class KeyStoreFactory {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new KeyStoreBytesModule());

  public static KeyStore loadFromJson(final String json) throws Exception {
    final KeyStoreData keyStoreData = OBJECT_MAPPER.readValue(json, KeyStoreData.class);
    return new KeyStore(keyStoreData);
  }

  public static KeyStore loadFromFile(final File keystoreFile) throws Exception {
    final KeyStoreData keyStoreData = OBJECT_MAPPER.readValue(keystoreFile, KeyStoreData.class);
    return new KeyStore(keyStoreData);
  }

  public static String toJson(final KeyStoreData keyStoreData) throws Exception {
    return KeyStoreFactory.OBJECT_MAPPER
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(keyStoreData);
  }
}
