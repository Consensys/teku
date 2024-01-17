/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.networking.p2p.network.config;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.KeyType;
import io.libp2p.core.crypto.PrivKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;

public class FilePrivateKeySource implements PrivateKeySource {
  private final String fileName;

  public FilePrivateKeySource(String fileName) {
    this.fileName = fileName;
  }

  @Override
  public Bytes getOrGeneratePrivateKeyBytes() {
    try {
      File file = new File(fileName);
      if (!file.createNewFile()) {
        return getPrivateKeyBytesFromFile();
      }
      final PrivKey privKey = KeyKt.generateKeyPair(KeyType.SECP256K1).component1();
      final Bytes privateKeyBytes = Bytes.wrap(KeyKt.marshalPrivateKey(privKey));
      Files.writeString(file.toPath(), privateKeyBytes.toHexString());
      STATUS_LOG.usingGeneratedP2pPrivateKey(fileName);
      return privateKeyBytes;

    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Not able to create or retrieve p2p private key file - " + fileName);
    }
  }

  private Bytes getPrivateKeyBytesFromFile() {
    try {
      return Bytes.fromHexString(Files.readString(Paths.get(fileName)));
    } catch (IOException e) {
      throw new RuntimeException("p2p private key file not found - " + fileName);
    }
  }

  public String getFileName() {
    return fileName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FilePrivateKeySource that = (FilePrivateKeySource) o;
    return Objects.equals(fileName, that.fileName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fileName);
  }
}
