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

package tech.pegasys.teku.protoarray;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.assertThatProtoArrayMatches;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProtoArraySnapshotTest {

  @Test
  void shouldProduceEqualProtoArray() {
    ProtoArray protoArray1 =
        ProtoArray.builder()
            .justifiedEpoch(UInt64.valueOf(10))
            .finalizedEpoch(UInt64.valueOf(9))
            .build();

    ProtoNode protoNode1 =
        new ProtoNode(
            UInt64.valueOf(10000),
            Bytes32.ZERO,
            Bytes32.fromHexString("0xdeadbeef"),
            Bytes32.ZERO,
            Optional.empty(),
            UInt64.valueOf(10),
            UInt64.valueOf(9),
            UInt64.ZERO,
            Optional.empty(),
            Optional.empty());

    protoArray1.onBlock(
        protoNode1.getBlockSlot(),
        protoNode1.getBlockRoot(),
        protoNode1.getParentRoot(),
        protoNode1.getStateRoot(),
        protoNode1.getJustifiedEpoch(),
        protoNode1.getFinalizedEpoch());

    // sanity check
    assertThat(protoArray1.getNodes().get(0)).isEqualTo(protoNode1);

    ProtoArraySnapshot snaphot = ProtoArraySnapshot.create(protoArray1);
    ProtoArray protoArray2 = snaphot.toProtoArray();

    assertThatProtoArrayMatches(protoArray1, protoArray2);
  }

  @Test
  void shouldNotBeAlteredByChangesToOriginalProtoArray() {
    ProtoArray protoArray1 =
        ProtoArray.builder()
            .justifiedEpoch(UInt64.valueOf(10))
            .finalizedEpoch(UInt64.valueOf(9))
            .build();

    ProtoNode protoNode1 =
        new ProtoNode(
            UInt64.valueOf(10000),
            Bytes32.ZERO,
            Bytes32.fromHexString("0xdeadbeef"),
            Bytes32.ZERO,
            Optional.empty(),
            UInt64.valueOf(10),
            UInt64.valueOf(9),
            UInt64.ZERO,
            Optional.empty(),
            Optional.empty());

    ProtoNode protoNode2 =
        new ProtoNode(
            UInt64.valueOf(10000),
            Bytes32.ZERO,
            Bytes32.fromHexString("0x1234"),
            Bytes32.ZERO,
            Optional.empty(),
            UInt64.valueOf(10),
            UInt64.valueOf(9),
            UInt64.ZERO,
            Optional.empty(),
            Optional.empty());

    protoArray1.onBlock(
        protoNode1.getBlockSlot(),
        protoNode1.getBlockRoot(),
        protoNode1.getParentRoot(),
        protoNode1.getStateRoot(),
        protoNode1.getJustifiedEpoch(),
        protoNode1.getFinalizedEpoch());

    protoArray1.onBlock(
        protoNode1.getBlockSlot(),
        protoNode1.getBlockRoot(),
        protoNode1.getParentRoot(),
        protoNode1.getStateRoot(),
        protoNode1.getJustifiedEpoch(),
        protoNode1.getFinalizedEpoch());

    // sanity check
    assertThat(protoArray1.getNodes().get(0)).isEqualTo(protoNode1);

    ProtoArraySnapshot snaphot = ProtoArraySnapshot.create(protoArray1);
    ProtoArray protoArray2 = snaphot.toProtoArray();

    assertThatProtoArrayMatches(protoArray1, protoArray2);

    protoArray1.getNodes().set(0, protoNode2);

    assertThatThrownBy(() -> assertThatProtoArrayMatches(protoArray1, protoArray2));
  }
}
