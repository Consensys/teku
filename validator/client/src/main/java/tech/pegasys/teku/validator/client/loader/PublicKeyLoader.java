/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.client.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class PublicKeyLoader {
  final ObjectMapper objectMapper;

  public PublicKeyLoader() {
    this(new ObjectMapper());
  }

  public PublicKeyLoader(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<BLSPublicKey> getPublicKeys(final List<String> sources) {
    if (sources == null || sources.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      final Set<BLSPublicKey> blsPublicKeySet =
          sources.stream()
              .flatMap(
                  key ->
                      key.contains(":")
                          ? readKeysFromUrl(key)
                          : Stream.of(BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key))))
              .collect(Collectors.toSet());

      return List.copyOf(blsPublicKeySet);
    } catch (IllegalArgumentException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Signer public key is invalid", e);
    }
  }

  private Stream<BLSPublicKey> readKeysFromUrl(final String url) {
    try {
      final String[] keys = objectMapper.readValue(new URL(url), String[].class);
      return Arrays.stream(keys)
          .map(key -> BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)));
    } catch (IOException ex) {
      throw new InvalidConfigurationException("Failed to load public keys from URL " + url, ex);
    }
  }
}
