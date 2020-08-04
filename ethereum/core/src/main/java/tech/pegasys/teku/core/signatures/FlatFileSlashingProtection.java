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

package tech.pegasys.teku.core.signatures;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class FlatFileSlashingProtection implements SlashingProtectionChannel {

  private final Map<BLSPublicKey, UnsignedLong> lastSignedBlock = new HashMap<>();
  private final Map<BLSPublicKey, SignedAttestationRecord> lastSignedAttestation = new HashMap<>();

  private final Path lastSignedBlocksDir;
  private final Path lastSignedAttestationsDir;

  public FlatFileSlashingProtection(final Path slashingProtectionBaseDir) {
    this.lastSignedBlocksDir = slashingProtectionBaseDir.resolve("blocks");
    this.lastSignedAttestationsDir = slashingProtectionBaseDir.resolve("attestations");
  }

  @Override
  public synchronized SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final UnsignedLong slot) {
    return SafeFuture.of(
        () -> {
          final Optional<UnsignedLong> lastSignedBlockSlot = getLastSignedBlockSlot(validator);

          if (!maySignBlock(slot, lastSignedBlockSlot)) {
            return false;
          }
          writeBlockSlot(validator, slot);
          return true;
        });
  }

  private Optional<UnsignedLong> getLastSignedBlockSlot(final BLSPublicKey validator)
      throws IOException {
    UnsignedLong lastSignedBlockSlot = lastSignedBlock.get(validator);
    if (lastSignedBlockSlot != null) {
      return Optional.of(lastSignedBlockSlot);
    }
    return loadLastSignedBlockSlot(validator);
  }

  private Boolean maySignBlock(
      final UnsignedLong slot, final Optional<UnsignedLong> lastSignedBlockSlot) {
    return lastSignedBlockSlot.map(lastSlot -> lastSlot.compareTo(slot) < 0).orElse(true);
  }

  private void writeBlockSlot(final BLSPublicKey publicKey, final UnsignedLong slot)
      throws IOException {
    syncedWrite(SSZ.encodeUInt64(slot.longValue()), blockSlotFile(publicKey));
    lastSignedBlock.put(publicKey, slot);
  }

  private Path blockSlotFile(final BLSPublicKey publicKey) {
    return lastSignedBlocksDir.resolve(publicKey.toBytesCompressed().toUnprefixedHexString());
  }

  private Optional<UnsignedLong> loadLastSignedBlockSlot(final BLSPublicKey publicKey)
      throws IOException {
    final Path path = blockSlotFile(publicKey);
    if (path.toFile().exists()) {
      return Optional.of(
          UnsignedLong.fromLongBits(SSZ.decodeUInt64(Bytes.wrap(Files.readAllBytes(path)))));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch) {
    return SafeFuture.of(
        () -> {
          final Optional<SignedAttestationRecord> lastSignedAttestationRecord =
              getLastSignedAttestationRecord(validator);
          if (!maySignAttestation(sourceEpoch, targetEpoch, lastSignedAttestationRecord)) {
            return false;
          }
          writeAttestationRecord(validator, sourceEpoch, targetEpoch);
          return true;
        });
  }

  private Optional<SignedAttestationRecord> getLastSignedAttestationRecord(
      final BLSPublicKey validator) throws IOException {
    SignedAttestationRecord signedAttestationRecord = lastSignedAttestation.get(validator);
    if (signedAttestationRecord != null) {
      return Optional.of(signedAttestationRecord);
    }
    return loadLastSignedAttestationRecord(validator);
  }

  private Optional<SignedAttestationRecord> loadLastSignedAttestationRecord(
      final BLSPublicKey validator) throws IOException {
    final Path recordPath = signedAttestationFile(validator);
    if (!recordPath.toFile().exists()) {
      return Optional.empty();
    }
    final Bytes data = Bytes.wrap(Files.readAllBytes(recordPath));
    return Optional.of(
        SSZ.decode(
            data,
            reader -> {
              final UnsignedLong sourceEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
              final UnsignedLong targetEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
              return new SignedAttestationRecord(sourceEpoch, targetEpoch);
            }));
  }

  private void writeAttestationRecord(
      final BLSPublicKey validator, final UnsignedLong sourceEpoch, final UnsignedLong targetEpoch)
      throws IOException {
    final Bytes data =
        SSZ.encode(
            writer -> {
              writer.writeUInt64(sourceEpoch.longValue());
              writer.writeUInt64(targetEpoch.longValue());
            });
    final Path path = signedAttestationFile(validator);
    syncedWrite(data, path);
    lastSignedAttestation.put(validator, new SignedAttestationRecord(sourceEpoch, targetEpoch));
  }

  private void syncedWrite(final Bytes data, final Path path) throws IOException {
    final File parentDirectory = path.getParent().toFile();
    if (!parentDirectory.mkdirs() && !parentDirectory.isDirectory()) {
      throw new IOException("Unable to create slashing protection directory " + parentDirectory);
    }
    Files.write(path, data.toArrayUnsafe(), StandardOpenOption.SYNC, StandardOpenOption.CREATE);
  }

  private Path signedAttestationFile(final BLSPublicKey validator) {
    return lastSignedAttestationsDir.resolve(validator.toBytesCompressed().toUnprefixedHexString());
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
