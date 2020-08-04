/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.slashingprotection;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.SlashingProtection;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.storage.server.Database;

public class SlashingProtectionStorage implements SlashingProtection {

  private static final SafeFuture<Boolean> SIGNING_DISALLOWED = SafeFuture.completedFuture(false);
  private static final SafeFuture<Boolean> SIGNING_ALLOWED = SafeFuture.completedFuture(true);
  private final Database database;

  public SlashingProtectionStorage(final Database database) {
    this.database = database;
  }

  @Override
  public SafeFuture<Boolean> maySignBlock(final BLSPublicKey validator, final UnsignedLong slot) {
    final Optional<UnsignedLong> lastSignedBlockSlot = database.getLatestSignedBlockSlot(validator);
    if (!maySignBlock(slot, lastSignedBlockSlot)) {
      return SIGNING_DISALLOWED;
    }
    database.recordLastSignedBlock(validator, slot);
    return SafeFuture.completedFuture(true);
  }

  private Boolean maySignBlock(
      final UnsignedLong slot, final Optional<UnsignedLong> lastSignedBlockSlot) {
    return lastSignedBlockSlot.map(lastSlot -> lastSlot.compareTo(slot) < 0).orElse(true);
  }

  @Override
  public SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch) {
    final Optional<SignedAttestationRecord> lastSignedAttestationRecord =
        database.getLastSignedAttestationRecord(validator);
    if (!maySignAttestation(sourceEpoch, targetEpoch, lastSignedAttestationRecord)) {
      return SIGNING_DISALLOWED;
    }
    database.recordLastSignedAttestation(
        validator, new SignedAttestationRecord(sourceEpoch, targetEpoch));
    return SIGNING_ALLOWED;
  }

  private boolean maySignAttestation(
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch,
      final Optional<SignedAttestationRecord> lastSignedAttestation) {
    return lastSignedAttestation
        .map(
            record ->
                record.getSourceEpoch().compareTo(sourceEpoch) <= 0
                    && record.getTargetEpoch().compareTo(targetEpoch) < 0)
        .orElse(true);
  }
}
