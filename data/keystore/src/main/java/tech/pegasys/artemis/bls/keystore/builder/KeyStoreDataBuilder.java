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

package tech.pegasys.artemis.bls.keystore.builder;

import java.util.Objects;
import java.util.UUID;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.keystore.Crypto;
import tech.pegasys.artemis.bls.keystore.KeyStoreData;

public final class KeyStoreDataBuilder {
  private Crypto crypto;
  private Bytes pubkey;
  private String path;
  private UUID uuid;

  private KeyStoreDataBuilder() {}

  public static KeyStoreDataBuilder aKeyStoreData() {
    return new KeyStoreDataBuilder();
  }

  public KeyStoreDataBuilder withCrypto(Crypto crypto) {
    this.crypto = crypto;
    return this;
  }

  public KeyStoreDataBuilder withPubkey(Bytes pubkey) {
    this.pubkey = pubkey;
    return this;
  }

  public KeyStoreDataBuilder withPath(String path) {
    this.path = path;
    return this;
  }

  public KeyStoreDataBuilder withUuid(UUID uuid) {
    this.uuid = uuid;
    return this;
  }

  public KeyStoreData build() {
    Objects.requireNonNull(crypto);
    Objects.requireNonNull(pubkey);
    if (uuid == null) {
      uuid = UUID.randomUUID();
    }

    if (path == null) {
      path = "";
    }
    return new KeyStoreData(crypto, pubkey, path, uuid, 4);
  }
}
