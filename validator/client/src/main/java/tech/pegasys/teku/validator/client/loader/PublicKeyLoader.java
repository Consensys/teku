/*
 * Copyright Consensys Software Inc., 2022
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
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;

public class PublicKeyLoader {
  public static final String EXTERNAL_SIGNER_PUBKEYS_ENDPOINT = "/api/v1/eth2/publicKeys";
  public static final String EXTERNAL_SIGNER_SOURCE_ID = "external-signer";

  final ObjectMapper objectMapper;
  final Supplier<HttpClient> externalSignerHttpClientFactory;
  final URL externalSignerUrl;
  final Optional<AsyncRunner> asyncRunner;

  @VisibleForTesting
  PublicKeyLoader() {
    this(
        new ObjectMapper(),
        () -> {
          throw new UnsupportedOperationException();
        },
        null,
        Optional.empty());
  }

  public PublicKeyLoader(
      final Supplier<HttpClient> externalSignerHttpClientFactory, final URL externalSignerUrl) {
    this(externalSignerHttpClientFactory, externalSignerUrl, Optional.empty());
  }

  public PublicKeyLoader(
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final URL externalSignerUrl,
      final Optional<AsyncRunner> asyncRunner) {
    this(new ObjectMapper(), externalSignerHttpClientFactory, externalSignerUrl, asyncRunner);
  }

  public PublicKeyLoader(
      final ObjectMapper objectMapper,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final URL externalSignerUrl,
      final Optional<AsyncRunner> asyncRunner) {
    this.objectMapper = objectMapper;
    this.externalSignerHttpClientFactory = externalSignerHttpClientFactory;
    this.externalSignerUrl = externalSignerUrl;
    this.asyncRunner = asyncRunner;
  }

  public List<BLSPublicKey> getPublicKeys(final List<String> sources) {
    return getPublicKeys(
        sources, (url) -> new ExternalUrlKeyReader(url, objectMapper, asyncRunner));
  }

  public List<BLSPublicKey> getPublicKeys(
      final List<String> sources, final Function<String, ExternalUrlKeyReader> urlReader) {
    if (sources == null || sources.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      final Set<BLSPublicKey> blsPublicKeySet =
          sources.stream()
              .flatMap(
                  key -> {
                    if (key.equalsIgnoreCase(EXTERNAL_SIGNER_SOURCE_ID)) {
                      return readKeysFromExternalSigner();
                    }
                    if (key.contains(":")) {
                      return urlReader.apply(key).readKeys();
                    }

                    return Stream.of(BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)));
                  })
              .collect(Collectors.toSet());

      return List.copyOf(blsPublicKeySet);
    } catch (IllegalArgumentException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Signer public key is invalid", e);
    }
  }

  private Stream<BLSPublicKey> readKeysFromExternalSigner() {
    try {
      final URI uri = externalSignerUrl.toURI().resolve(EXTERNAL_SIGNER_PUBKEYS_ENDPOINT);
      final HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
      final HttpResponse<String> response =
          externalSignerHttpClientFactory.get().send(request, BodyHandlers.ofString());
      if (response.statusCode() != HttpStatusCodes.SC_OK) {
        throw new ExternalSignerException(
            "Public Keys endpoint returned " + response.statusCode() + " status code");
      }
      final String[] keys = objectMapper.readValue(response.body(), String[].class);
      return Arrays.stream(keys).map(key -> BLSPublicKey.fromSSZBytes(Bytes.fromHexString(key)));
    } catch (URISyntaxException | IOException | ExternalSignerException | InterruptedException ex) {
      throw new InvalidConfigurationException(
          "Failed to load public keys from external signer.", ex);
    }
  }

  static class ExternalSignerException extends Exception {
    ExternalSignerException(final String message) {
      super(message);
    }
  }
}
