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

package tech.pegasys.teku.statetransition.execution;

import static tech.pegasys.teku.spec.config.Constants.MAX_SLOTS_TO_TRACK_PROPOSER_PREFERENCES;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerPreferencesGossipValidator;

public class DefaultProposerPreferencesManager implements ProposerPreferencesManager {

  private final ProposerPreferencesGossipValidator proposerPreferencesGossipValidator;
  private final Map<UInt64, ProposerPreferences> acceptedProposerPreferences =
      LimitedMap.createSynchronizedLRU(MAX_SLOTS_TO_TRACK_PROPOSER_PREFERENCES);
  private final List<Consumer<SignedProposerPreferences>> acceptedListeners = new ArrayList<>();

  public DefaultProposerPreferencesManager(
      final ProposerPreferencesGossipValidator proposerPreferencesGossipValidator) {
    this.proposerPreferencesGossipValidator = proposerPreferencesGossipValidator;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateAndAddProposerPreferences(
      final SignedProposerPreferences signedProposerPreferences) {
    return proposerPreferencesGossipValidator
        .validate(signedProposerPreferences)
        .thenApply(
            result -> {
              if (result.isAccept()) {
                acceptedProposerPreferences.put(
                    signedProposerPreferences.getMessage().getProposalSlot(),
                    signedProposerPreferences.getMessage());
                acceptedListeners.forEach(listener -> listener.accept(signedProposerPreferences));
              }
              return result;
            });
  }

  @Override
  public Optional<ProposerPreferences> getProposerPreferences(final UInt64 slot) {
    return Optional.ofNullable(acceptedProposerPreferences.get(slot));
  }

  @Override
  public void subscribeAcceptedProposerPreferences(
      final Consumer<SignedProposerPreferences> listener) {
    acceptedListeners.add(listener);
  }
}
