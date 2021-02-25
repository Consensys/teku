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

package tech.pegasys.teku.spec.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.internal.StubSpecProvider;

public class CommitteeUtilTest {
  final SpecConstants specConstants = mock(SpecConstants.class);
  private final SpecProvider specProvider = StubSpecProvider.create();
  CommitteeUtil committeeUtil = new CommitteeUtil(specConstants);

  @Test
  void aggregatorModulo_boundaryTest() {
    when(specConstants.getTargetAggregatorsPerCommittee()).thenReturn(100);
    // invalid cases technically
    assertThat(committeeUtil.getAggregatorModulo(Integer.MIN_VALUE)).isEqualTo(1);
    assertThat(committeeUtil.getAggregatorModulo(-1)).isEqualTo(1);

    // practical lower bounds
    assertThat(committeeUtil.getAggregatorModulo(0)).isEqualTo(1);
    assertThat(committeeUtil.getAggregatorModulo(199)).isEqualTo(1);

    // upper bound - valid
    assertThat(committeeUtil.getAggregatorModulo(Integer.MAX_VALUE)).isEqualTo(21474836);
  }

  @Test
  void aggregatorModulo_samples() {
    when(specConstants.getTargetAggregatorsPerCommittee()).thenReturn(100);

    assertThat(committeeUtil.getAggregatorModulo(200)).isEqualTo(2);
    assertThat(committeeUtil.getAggregatorModulo(300)).isEqualTo(3);
    assertThat(committeeUtil.getAggregatorModulo(1000)).isEqualTo(10);
    assertThat(committeeUtil.getAggregatorModulo(100000)).isEqualTo(1000);
  }

  @Test
  void computeShuffledIndex_boundaryTest() {
    assertThatThrownBy(() -> committeeUtil.computeShuffledIndex(2, 1, Bytes32.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void computeShuffledIndex_samples() {
    when(specConstants.getShuffleRoundCount()).thenReturn(90);
    assertThat(committeeUtil.computeShuffledIndex(320, 2048, Bytes32.ZERO)).isEqualTo(0);
    assertThat(committeeUtil.computeShuffledIndex(1291, 2048, Bytes32.ZERO)).isEqualTo(1);
    assertThat(committeeUtil.computeShuffledIndex(933, 2048, Bytes32.ZERO)).isEqualTo(2047);
  }

  @Test
  void computeShuffledIndex_testListShuffleAndShuffledIndexCompatibility() {
    when(specConstants.getShuffleRoundCount()).thenReturn(10);
    Bytes32 seed = Bytes32.ZERO;
    int index_count = 3333;
    int[] indexes = IntStream.range(0, index_count).toArray();

    tech.pegasys.teku.spec.datastructures.util.CommitteeUtil.shuffle_list(indexes, seed);
    assertThat(indexes)
        .isEqualTo(
            IntStream.range(0, index_count)
                .map(i -> committeeUtil.computeShuffledIndex(i, indexes.length, seed))
                .toArray());
  }

  @Test
  void testIsAggregatorReturnsFalseOnARealCase() {
    Bytes signingRoot =
        specProvider
            .atSlot(UInt64.ZERO)
            .getBeaconStateUtil()
            .computeSigningRoot(
                UInt64.valueOf(57950),
                Bytes32.fromHexString(
                    "0x05000000b5303f2ad2010d699a76c8e62350947421a3e4a979779642cfdb0f66"));
    BLSSignature selectionProof =
        BLSSignature.fromSSZBytes(
            Bytes.fromHexString(
                "0xaa176502f0a5e954e4c6b452d0e11a03513c19b6d189f125f07b6c5c120df011c31da4c4a9c4a52a5a48fcba5b14d7b316b986a146187966d2341388bbf1f86c42e90553ba009ba10edc6b5544a6e945ce6d2419197f66ab2b9df2b0a0c89987"));
    BLSPublicKey pKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0xb0861f72583516b17a3fdc33419d5c04c0a4444cc2478136b4935f3148797699e3ef4a4b2227b14876b3d49ff03b796d"));
    int committeeLen = 146;

    assertThat(BLS.verify(pKey, signingRoot, selectionProof)).isTrue();

    int aggregatorModulo =
        specProvider.atSlot(UInt64.ZERO).getCommitteeUtil().getAggregatorModulo(committeeLen);
    assertThat(CommitteeUtil.isAggregator(selectionProof, aggregatorModulo)).isFalse();
  }
}
