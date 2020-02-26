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

package tech.pegasys.artemis.bls.keystore.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.UUID;
import org.apache.tuweni.bytes.Bytes;

/**
 * BLS Key Store with Jackson Bindings as per json schema.
 *
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2335.md">EIP-2335</a>
 */
public class KeyStoreData {
  public static final int KEYSTORE_VERSION = 4;
  private final Crypto crypto;
  private final Bytes pubkey;
  private final String path;
  private final UUID uuid;
  private final Integer version;

  @JsonCreator
  public KeyStoreData(
      @JsonProperty(value = "crypto", required = true) final Crypto crypto,
      @JsonProperty(value = "pubkey", required = true) final Bytes pubkey,
      @JsonProperty(value = "path", required = true) final String path,
      @JsonProperty(value = "uuid", required = true) final UUID uuid,
      @JsonProperty(value = "version", required = true) final Integer version) {
    this.crypto = crypto;
    this.pubkey = pubkey;
    this.path = path;
    this.uuid = uuid;
    this.version = version;
  }

  public KeyStoreData(final Crypto crypto, final Bytes pubkey, final String path) {
    this(crypto, pubkey, path, UUID.randomUUID(), KEYSTORE_VERSION);
  }

  public Crypto getCrypto() {
    return crypto;
  }

  public Bytes getPubkey() {
    return pubkey;
  }

  public String getPath() {
    return path;
  }

  public UUID getUuid() {
    return uuid;
  }

  public Integer getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("crypto", crypto)
        .add("pubkey", pubkey)
        .add("path", path)
        .add("uuid", uuid)
        .add("version", version)
        .toString();
  }
}
