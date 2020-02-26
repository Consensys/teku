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

package tech.pegasys.artemis.bls.keystore;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.util.json.BytesModule;

/** Provide utility methods to load/store BLS KeyStore from json format */
public class KeyStoreLoader {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new BytesModule());

  public static KeyStoreData loadFromJson(final String json) throws Exception {
    requireNonNull(json);
    final KeyStoreData keyStoreData = OBJECT_MAPPER.readValue(json, KeyStoreData.class);
    // TODO: perform validation
    return keyStoreData;
  }

  public static KeyStoreData loadFromFile(final Path keystoreFile) throws Exception {
    requireNonNull(keystoreFile);
    final KeyStoreData keyStoreData =
        OBJECT_MAPPER.readValue(keystoreFile.toFile(), KeyStoreData.class);
    // TODO: perform validation
    return keyStoreData;
  }

  public static String toJson(final KeyStoreData keyStoreData) throws Exception {
    return KeyStoreLoader.OBJECT_MAPPER
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(keyStoreData);
  }
}
