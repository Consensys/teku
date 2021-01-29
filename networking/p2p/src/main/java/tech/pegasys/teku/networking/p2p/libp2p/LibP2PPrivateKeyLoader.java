/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.p2p.libp2p;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.store.KeyValueStore;

public class LibP2PPrivateKeyLoader implements LibP2PNetwork.PrivateKeyProvider {
  static final String GENERATED_NODE_KEY_KEY = "generated-node-key";
  private final KeyValueStore<String, Bytes> keyValueStore;
  private final Optional<String> privateKeyFile;

  public LibP2PPrivateKeyLoader(
      final KeyValueStore<String, Bytes> keyValueStore, final Optional<String> privateKeyFile) {
    this.keyValueStore = keyValueStore;
    this.privateKeyFile = privateKeyFile;
  }

  public static PrivKey loadPrivateKey(
      KeyValueStore<String, Bytes> keyValueStore, final Optional<String> privateKeyFile) {
    return new LibP2PPrivateKeyLoader(keyValueStore, privateKeyFile).get();
  }

  @Override
  public PrivKey get() {
    final Bytes privKeyBytes =
        privateKeyFile.map(this::loadBytesFromFile).orElseGet(this::generateNewPrivateKey);
    return KeyKt.unmarshalPrivateKey(privKeyBytes.toArrayUnsafe());
  }

  private Bytes generateNewPrivateKey() {
    final Bytes privateKey;
    final Optional<Bytes> generatedKeyBytes = keyValueStore.get(GENERATED_NODE_KEY_KEY);
    if (generatedKeyBytes.isEmpty()) {
      final PrivKey privKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
      privateKey = Bytes.wrap(KeyKt.marshalPrivateKey(privKey));
      keyValueStore.put(GENERATED_NODE_KEY_KEY, privateKey);
      STATUS_LOG.usingGeneratedP2pPrivateKey(GENERATED_NODE_KEY_KEY, true);
    } else {
      privateKey = generatedKeyBytes.get();
      STATUS_LOG.usingGeneratedP2pPrivateKey(GENERATED_NODE_KEY_KEY, false);
    }
    return privateKey;
  }

  private Bytes loadBytesFromFile(final String privateKeyFile) {
    try {
      return Bytes.fromHexString(Files.readString(Paths.get(privateKeyFile)));
    } catch (IOException e) {
      throw new RuntimeException("p2p private key file not found - " + privateKeyFile);
    }
  }
}
