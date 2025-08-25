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

package tech.pegasys.teku.spec.config;

import java.util.Optional;

public interface SpecConfigGloas extends SpecConfigFulu, NetworkingSpecConfigGloas {

  static SpecConfigGloas required(final SpecConfig specConfig) {
    return specConfig
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected gloas spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  @Override
  Optional<SpecConfigGloas> toVersionGloas();

  int getPayloadAttestationDueBps();

  int getPtcSize();

  int getMaxPayloadAttestations();
}
