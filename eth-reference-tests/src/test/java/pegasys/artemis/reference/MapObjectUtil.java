/*
 * Copyright 2019 ConsenSys AG.
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

package pegasys.artemis.reference;

import com.google.common.primitives.UnsignedLong;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class MapObjectUtil {

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Attestation getAttestation(Map map) {
    return new Attestation(
        Bytes.fromHexString(map.get("aggregation_bits").toString()),
        getAttestationData((Map) map.get("data")),
        Bytes.fromHexString(map.get("custody_bits").toString()),
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString())));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static AttestationData getAttestationData(Map map) {
    return new AttestationData(
        Bytes32.fromHexString(map.get("beacon_block_root").toString()),
        getCheckpoint((Map) map.get("source")),
        getCheckpoint((Map) map.get("target")),
        getCrossLink((Map) map.get("crosslink")));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Checkpoint getCheckpoint(Map map) {
    return new Checkpoint(
        UnsignedLong.valueOf(map.get("epoch").toString()),
        Bytes32.fromHexString(map.get("root").toString()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Crosslink getCrossLink(Map map) {
    return new Crosslink(
        UnsignedLong.valueOf(map.get("shard").toString()),
        Bytes32.fromHexString(map.get("parent_root").toString()),
        UnsignedLong.valueOf(map.get("start_epoch").toString()),
        UnsignedLong.valueOf(map.get("end_epoch").toString()),
        Bytes32.fromHexString(map.get("data_root").toString()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Bytes32 getBytes32(Map testObject) {
    return Bytes32.fromHexString(testObject.toString());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Bytes getBytes(Map testObject) {
    return Bytes.fromHexString(testObject.toString());
  }
}
