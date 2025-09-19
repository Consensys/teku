/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.client;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.api.GraffitiProvider;

public class Validator {
  private static final Logger LOG = LogManager.getLogger();

  private final BLSPublicKey publicKey;
  private final Signer signer;
  private final GraffitiProvider graffitiProvider;
  private final boolean readOnly;

  public Validator(
      final BLSPublicKey publicKey, final Signer signer, final GraffitiProvider graffitiProvider) {
    this(publicKey, signer, graffitiProvider, true);
  }

  public Validator(
      final BLSPublicKey publicKey,
      final Signer signer,
      final GraffitiProvider graffitiProvider,
      final boolean readOnly) {
    this.publicKey = publicKey;
    this.signer = signer;
    this.graffitiProvider = graffitiProvider;
    this.readOnly = readOnly;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public Signer getSigner() {
    return signer;
  }

  public Optional<Bytes32> getGraffiti() {
    try {
      return graffitiProvider.get();
    } catch (final Exception e) {
      // Log the error but don't let it impact block production
      // Note: This is a safety net. Individual GraffitiProvider implementations
      // should handle their own errors, but this ensures block production continues
      // even if they don't.
      LOG.warn("Error getting graffiti from graffiti provider: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public GraffitiProvider getGraffitiProvider() {
    return graffitiProvider;
  }

  @Override
  public String toString() {
    return "Validator{" + "publicKey=" + publicKey + '}';
  }
}
