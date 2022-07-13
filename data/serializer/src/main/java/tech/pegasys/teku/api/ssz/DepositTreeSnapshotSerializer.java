/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.ssz;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.api.data.DepositTreeSnapshotData;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositTreeSnapshotSerializer implements SszSerializer<DepositTreeSnapshotData> {
  public static final DepositTreeSnapshotSerializer INSTANCE = new DepositTreeSnapshotSerializer();
  public static final OctetStreamResponseContentTypeDefinition<DepositTreeSnapshotData>
      OCTET_TYPE_DEFINITION =
          new OctetStreamResponseContentTypeDefinition<>(
              (data, out) -> out.write(INSTANCE.serialize(data)),
              depositTreeSnapshotData ->
                  Map.of(
                      RestApiConstants.HEADER_CONTENT_DISPOSITION,
                      getSszFilename(depositTreeSnapshotData)));

  private static String getSszFilename(final DepositTreeSnapshotData value) {
    return String.format("filename=\"DepositTreeSnapshot-%s.ssz\"", value.getExecutionBlockHash());
  }

  @Override
  public byte[] serialize(final DepositTreeSnapshotData value) {
    final Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeFixedBytesList(value.getFinalized());
              writer.writeUInt64(value.getDeposits().longValue());
              writer.writeFixedBytes(value.getExecutionBlockHash());
            });
    return bytes.toArrayUnsafe();
  }

  @Override
  public DepositTreeSnapshotData deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final List<Bytes32> finalized =
              // Tuweni's `reader.readFixedBytesList` is buggy, using this instead
              reader.readHashList(Bytes32.SIZE).stream()
                  .map(Bytes32::wrap)
                  .collect(Collectors.toList());
          final UInt64 deposits = UInt64.fromLongBits(reader.readUInt64());
          final Bytes32 executionBlockHash = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          return new DepositTreeSnapshotData(finalized, deposits, executionBlockHash);
        });
  }
}
