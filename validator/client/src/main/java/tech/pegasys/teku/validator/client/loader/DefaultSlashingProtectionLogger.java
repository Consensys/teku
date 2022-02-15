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

package tech.pegasys.teku.validator.client.loader;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.StringJoiner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

public class DefaultSlashingProtectionLogger implements SlashingProtectionLogger {
  private static final Logger LOG = LogManager.getLogger("Slashing Protection");

  private final SlashingProtector slashingProtector;
  private final ForkProvider forkProvider;

  public DefaultSlashingProtectionLogger(
      final SlashingProtector slashingProtector, final ForkProvider forkProvider) {
    this.slashingProtector = slashingProtector;
    this.forkProvider = forkProvider;
  }

  @Override
  public void logSlashingProtection(final List<Validator> validators) {
    final StringJoiner sj = new StringJoiner("\n");
    sj.add("Slashing Protection activated. Validators summary:");
    forkProvider
        .getForkInfo(GENESIS_SLOT)
        .thenPeek(
            forkInfo -> {
              final ValidatorSigningRecord emptySigningRecord =
                  new ValidatorSigningRecord(forkInfo.getGenesisValidatorsRoot());
              SafeFuture.collectAll(
                      validators.stream()
                          .map(
                              validator ->
                                  presentValidatorSlashingProtection(
                                      validator, forkInfo, emptySigningRecord)))
                  .thenAccept(
                      spInfoList -> {
                        spInfoList.forEach(sj::add);
                        LOG.info(sj.toString());
                      })
                  .finish(
                      ex ->
                          LOG.error(
                              "Failed to present validators Slashing Protection summary", ex));
            })
        .finish(ex -> LOG.error("Failed to present validators Slashing Protection summary", ex));
  }

  @VisibleForTesting
  public SafeFuture<String> presentValidatorSlashingProtection(
      final Validator validator,
      final ForkInfo forkInfo,
      final ValidatorSigningRecord emptySigningRecord) {
    if (validator.getSigner().isSlashingProtectedLocally()) {
      return slashingProtector
          .loadSigningRecord(validator.getPublicKey(), forkInfo.getGenesisValidatorsRoot())
          .thenApply(
              signingRecord ->
                  presentValidatorWithSlashingProtection(
                      validator, signingRecord, emptySigningRecord));
    } else {
      return SafeFuture.of(() -> presentValidatorWithoutSlashingProtection(validator));
    }
  }

  private String presentValidatorWithoutSlashingProtection(final Validator validator) {
    return String.format("%s: not protected", validator.getPublicKey().toAbbreviatedString());
  }

  private String presentValidatorWithSlashingProtection(
      final Validator validator,
      final ValidatorSigningRecord signingRecord,
      final ValidatorSigningRecord emptySigningRecord) {
    StringBuilder sb = new StringBuilder(validator.getPublicKey().toAbbreviatedString());
    sb.append(": ");
    if (signingRecord.equals(emptySigningRecord)) {
      sb.append("protected");
    } else {
      sb.append("protected, saved latest signature for slot #")
          .append(signingRecord.getBlockSlot());
      if (signingRecord.getAttestationSourceEpoch() != null) {
        sb.append(", attestation (epochs ")
            .append(signingRecord.getAttestationSourceEpoch())
            .append(" -> ")
            .append(signingRecord.getAttestationTargetEpoch())
            .append(")");
      }
    }
    return sb.toString();
  }
}
