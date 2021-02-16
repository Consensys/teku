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

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

public class LibP2PPrivateKeyLoaderTest {
  private final MemKeyValueStore<String, Bytes> store = new MemKeyValueStore<>();

  @Test
  void testPrivateKeyGeneration() {
    final LibP2PPrivateKeyLoader loader = new LibP2PPrivateKeyLoader(store, Optional.empty());

    // check that new key is generated
    final PrivKey generatedPK = loader.get();
    assertThat(generatedPK).isNotNull();
    assertRoundTrip(generatedPK);

    // check the same key loaded next time
    PrivKey loadedPK = loader.get();
    assertThat(loadedPK).isEqualTo(generatedPK);

    // If store is cleared, we should generate a new key
    store.remove(LibP2PPrivateKeyLoader.GENERATED_NODE_KEY_KEY);

    // check that another key is generated after old key is deleted
    PrivKey generatedAnotherPK = loader.get();
    assertThat(generatedAnotherPK).isNotNull();
    assertRoundTrip(generatedAnotherPK);
    assertThat(generatedAnotherPK).isNotEqualTo(generatedPK);
  }

  @Test
  void testPrivateKeyLoaded(@TempDir final Path tmpDir) throws IOException {
    // check that user supplied private key file has precedence over generated file
    Path customPKFile = tmpDir.resolve("customPK.hex");
    final PrivKey privKey = PrivateKeyGenerator.generate();
    final Bytes privKeyBytes = Bytes.wrap(privKey.bytes());
    Files.writeString(customPKFile, privKeyBytes.toHexString());

    final LibP2PPrivateKeyLoader loader =
        new LibP2PPrivateKeyLoader(store, Optional.of(customPKFile.toAbsolutePath().toString()));
    assertThat(loader.get()).isEqualTo(privKey);
  }

  private void assertRoundTrip(final PrivKey generatedPK) {
    final PrivKey reparsed = KeyKt.unmarshalPrivateKey(generatedPK.bytes());
    assertThat(reparsed).isEqualTo(generatedPK);
  }
}
