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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import static java.util.stream.Collectors.toList;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.Deposit;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;

public class DepositsFromBlockEventSerializer implements RocksDbSerializer<DepositsFromBlockEvent> {

  @Override
  public DepositsFromBlockEvent deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final UInt64 blockNumber = UInt64.fromLongBits(reader.readUInt64());
          final Bytes32 blockHash = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final UInt64 blockTimestamp = UInt64.fromLongBits(reader.readUInt64());
          final Stream<Deposit> depositsStream =
              reader.readBytesList().stream().map(this::decodeDeposit);
          return DepositsFromBlockEvent.create(
              blockNumber, blockHash, blockTimestamp, depositsStream);
        });
  }

  @Override
  public byte[] serialize(final DepositsFromBlockEvent value) {
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeUInt64(value.getBlockNumber().longValue());
              writer.writeFixedBytes(value.getBlockHash());
              writer.writeUInt64(value.getBlockTimestamp().longValue());
              writer.writeBytesList(
                  value.getDeposits().stream().map(this::encodeDeposit).collect(toList()));
            });
    return bytes.toArrayUnsafe();
  }

  private Bytes encodeDeposit(Deposit deposit) {
    return SSZ.encode(
        depositWriter -> {
          depositWriter.writeFixedBytes(deposit.getPubkey().toBytesCompressed());
          depositWriter.writeFixedBytes(deposit.getWithdrawal_credentials());
          depositWriter.writeFixedBytes(deposit.getSignature().toSSZBytes());
          depositWriter.writeUInt64(deposit.getAmount().longValue());
          depositWriter.writeUInt64(deposit.getMerkle_tree_index().longValue());
        });
  }

  private Deposit decodeDeposit(final Bytes data) {
    return SSZ.decode(
        data,
        reader -> {
          final BLSPublicKey publicKey =
              BLSPublicKey.fromSSZBytes(
                  Bytes.wrap(reader.readFixedBytes(BLSPublicKey.SSZ_BLS_PUBKEY_SIZE)));
          final Bytes32 withdrawalCredentials = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final BLSSignature signature =
              BLSSignature.fromSSZBytes(reader.readFixedBytes(BLSSignature.SSZ_BLS_SIGNATURE_SIZE));
          final UInt64 amount = UInt64.fromLongBits(reader.readUInt64());
          final UInt64 merkleTreeIndex = UInt64.fromLongBits(reader.readUInt64());
          return new Deposit(publicKey, withdrawalCredentials, signature, amount, merkleTreeIndex);
        });
  }
}
