/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class JwtSecretKeyLoader {
  private static final Logger LOG = LogManager.getLogger();
  private final Optional<String> jwtSecretFile;

  public JwtSecretKeyLoader(final Optional<String> jwtSecretFile) {
    this.jwtSecretFile = jwtSecretFile;
  }

  public Key getSecretKey() {
    return jwtSecretFile.map(this::loadSecretFromFile).orElseGet(this::generateNewSecret);
  }

  private Key generateNewSecret() {
    LOG.info("generating new execution engine JWT secret");
    final Key key = MacProvider.generateKey(SignatureAlgorithm.HS256);
    final byte[] keyData = key.getEncoded();
    return new SecretKeySpec(keyData, SignatureAlgorithm.HS256.getJcaName());
    // TODO: write secret to file
  }

  private Key loadSecretFromFile(final String jwtSecretFile) {
    try {
      final Bytes bytesFromHex = Bytes.fromHexString(Files.readString(Paths.get(jwtSecretFile)));
      return new SecretKeySpec(bytesFromHex.toArray(), SignatureAlgorithm.HS256.getJcaName());
    } catch (IOException e) {
      throw new RuntimeException("unable to load execution engine JWT secret - " + jwtSecretFile);
    }
  }
}
