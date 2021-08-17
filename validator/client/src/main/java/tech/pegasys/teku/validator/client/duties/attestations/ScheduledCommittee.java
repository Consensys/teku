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

package tech.pegasys.teku.validator.client.duties.attestations;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.client.Validator;

class ScheduledCommittee {

  private final List<ValidatorWithCommitteePositionAndIndex> validators = new ArrayList<>();
  private final SafeFuture<Optional<AttestationData>> attestationDataFuture = new SafeFuture<>();

  public synchronized void addValidator(
      final Validator validator,
      final int committeePosition,
      final int validatorIndex,
      final int committeeSize) {
    validators.add(
        new ValidatorWithCommitteePositionAndIndex(
            validator, committeePosition, validatorIndex, committeeSize));
  }

  public synchronized <T> List<SafeFuture<T>> forEach(
      final Function<ValidatorWithCommitteePositionAndIndex, SafeFuture<T>> action) {
    return validators.stream().map(action).collect(toList());
  }

  public List<ValidatorWithCommitteePositionAndIndex> getValidators() {
    return validators;
  }

  public Set<BLSPublicKey> getValidatorPublicKeys() {
    return validators.stream()
        .map(ValidatorWithCommitteePositionAndIndex::getPublicKey)
        .collect(toSet());
  }

  public SafeFuture<Optional<AttestationData>> getAttestationDataFuture() {
    return attestationDataFuture;
  }

  @Override
  public String toString() {
    return "Committee{" + validators + '}';
  }
}
