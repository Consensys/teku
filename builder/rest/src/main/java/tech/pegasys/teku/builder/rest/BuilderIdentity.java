/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.builder.rest;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_PRIMITIVE_TYPE;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public record BuilderIdentity(BLSPublicKey pubkey, boolean needsAuth) {

  public static final DeserializableTypeDefinition<BuilderIdentity> TYPE =
      withDataWrapper(
          "GetBuilderIdentityResponse",
          DeserializableTypeDefinition.object(BuilderIdentity.class, BuilderIdentityBuilder.class)
              .initializer(BuilderIdentityBuilder::new)
              .finisher(BuilderIdentityBuilder::build)
              .withField(
                  "pubkey", PUBLIC_KEY_TYPE, BuilderIdentity::pubkey, BuilderIdentityBuilder::pubkey)
              .withField(
                  "needs_auth",
                  BOOLEAN_PRIMITIVE_TYPE,
                  BuilderIdentity::needsAuth,
                  BuilderIdentityBuilder::needsAuth)
              .build());

  static class BuilderIdentityBuilder {
    private BLSPublicKey pubkey;
    private boolean needsAuth;

    BuilderIdentityBuilder pubkey(final BLSPublicKey pubkey) {
      this.pubkey = pubkey;
      return this;
    }

    BuilderIdentityBuilder needsAuth(final boolean needsAuth) {
      this.needsAuth = needsAuth;
      return this;
    }

    BuilderIdentity build() {
      return new BuilderIdentity(pubkey, needsAuth);
    }
  }
}
