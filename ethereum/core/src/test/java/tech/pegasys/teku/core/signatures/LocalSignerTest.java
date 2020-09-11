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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class LocalSignerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();

  private static final BLSKeyPair KEYPAIR = BLSKeyPair.random(1234);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final LocalSigner signer = new LocalSigner(KEYPAIR, asyncRunner);

  @Test
  public void shouldSignBlock() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "luIZGEgsjSbFo4MEPVeqaqqm1AnnTODcxFy9gPmdAywVmDIpqkzYed8DJ2l4zx5WAejUTox+NO5HQ4M2APMNovd7FuqnCSVUEftrL4WtJqegPrING2ZCtVTrcaUzFpUQ"));

    final SafeFuture<BLSSignature> result = signer.signBlock(block, fork);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(expectedSignature);
  }

  @Test
  public void shouldCreateRandaoReveal() {
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "j7vOT7GQBnv+aIqxb0byMWNvMCXhQwAfj38UcMne7pNGXOvNZKnXQ9Knma/NOPUyAvLcRBDtew23vVtzWcm7naaTRJVvLJS6xiPOMIHOw6wNtGggzc20heZAXZAMdaKi"));
    final SafeFuture<BLSSignature> reveal = signer.createRandaoReveal(UInt64.valueOf(7), fork);
    asyncRunner.executeQueuedActions();

    assertThat(reveal).isCompletedWithValue(expectedSignature);
  }

  @Test
  public void shouldSignAttestationData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "l1DUv3fmbvZanhCaaraMk2PKAl+33sf3UHMbxkv18CKILzzIz+Hr6hnLXCHqWQYEGKTtLcf6OLV7Z+Y21BW2bBtJHXJqqzvWkec/j0X0hWaEoWOSAs20sipO1WSIUY2m"));

    final SafeFuture<BLSSignature> result = signer.signAttestationData(attestationData, fork);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(expectedSignature);
  }

  @Test
  public void shouldSignAggregationSlot() {
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "hnCLCZlbEyzMFq2JLHl6wk4W6gpbFGoQA2N4WB+CpgqVg3gcxJpRKOswtSTU4XdSEU2x3Hf0oTlxer/gVaFwAh84Mm4VLH67LNUxVO4+o2Q5TxOD1sArnvMcOJdGMGp2"));

    final SafeFuture<BLSSignature> result = signer.signAggregationSlot(UInt64.valueOf(7), fork);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(expectedSignature);
  }

  @Test
  public void shouldSignAggregateAndProof() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "kHbIuvFcS/kDppbCj0ILOU27ZjSU1P2wPsOKBBwGaz1uvXQxtUXQAdbybN1zotZqCs6pstChIIxDS/WgAZH2z4yX2cM/cM/iKayT2rZZJuu31V2uxP1AYVcyHLEMtF07"));

    final SafeFuture<BLSSignature> result = signer.signAggregateAndProof(aggregateAndProof, fork);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(expectedSignature);
  }

  @Test
  public void shouldSignVoluntaryExit() {
    final VoluntaryExit voluntaryExit = dataStructureUtil.randomVoluntaryExit();
    final BLSSignature expectedSignature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromBase64String(
                "g9JMIY7595zlrapmwbnCLj8+WX7ry3yfBwNNPQ9mRJ0m+rXTwgDpmsxpzs+kX4F8Bg+KRz+v5BPKEAWkeh8bJBDX7psiELLI3q9WmCX95MXT080jByrtYLdz1Qy3OUKK"));

    final SafeFuture<BLSSignature> result = signer.signVoluntaryExit(voluntaryExit, fork);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(expectedSignature);
  }
}
