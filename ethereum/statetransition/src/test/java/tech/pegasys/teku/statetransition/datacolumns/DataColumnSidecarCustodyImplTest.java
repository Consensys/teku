/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnSidecarCustodyImplTest {

  final Spec spec = TestSpecFactory.createMinimalEip7594();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);
  final UInt256 myNodeId = UInt256.ONE;

  final SpecConfigEip7594 config =
      SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
  final int subnetCount = config.getDataColumnSidecarSubnetCount();
  final int custodyCount = config.getCustodyRequirement();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  private DataColumnSidecar createSidecar(BeaconBlock block, int column) {
    return dataStructureUtil.randomDataColumnSidecar(createSigned(block), UInt64.valueOf(column));
  }

  private SignedBeaconBlockHeader createSigned(BeaconBlock block) {
    return dataStructureUtil.signedBlock(block).asHeader();
  }

  @Test
  @SuppressWarnings("JavaCase")
  void sanityTest() throws Throwable {
    DataColumnSidecarCustodyImpl custody =
        new DataColumnSidecarCustodyImpl(
            spec, blockResolver, db, myNodeId, subnetCount, Duration.ofMillis(200));
    BeaconBlock block = blockResolver.addBlock(10, true);
    DataColumnSidecar sidecar0 = createSidecar(block, 0);
    DataColumnIdentifier columnId0 = DataColumnIdentifier.createFromSidecar(sidecar0);

    SafeFuture<Optional<DataColumnSidecar>> fRet1 = custody.getCustodyDataColumnSidecar(columnId0);
    Optional<DataColumnSidecar> ret1 = fRet1.get(1, TimeUnit.SECONDS);

    assertThat(ret1).isEmpty();

    SafeFuture<Optional<DataColumnSidecar>> fRet2_1 =
        custody.getCustodyDataColumnSidecar(columnId0);
    SafeFuture<Optional<DataColumnSidecar>> fRet2_2 =
        custody.getCustodyDataColumnSidecar(columnId0);

    custody.onNewValidatedDataColumnSidecar(sidecar0);

    assertThat(fRet2_1.get().get()).isEqualTo(sidecar0);
    assertThat(fRet2_2.get().get()).isEqualTo(sidecar0);

    SafeFuture<Optional<DataColumnSidecar>> fRet3 = custody.getCustodyDataColumnSidecar(columnId0);

    assertThat(fRet3.get().get()).isEqualTo(sidecar0);

    DataColumnSidecar sidecar1 = createSidecar(block, 1);
    DataColumnIdentifier columnId1 = DataColumnIdentifier.createFromSidecar(sidecar1);

    SafeFuture<Optional<DataColumnSidecar>> fRet4 = custody.getCustodyDataColumnSidecar(columnId1);
    assertThat(fRet4).isNotDone();

    custody.onNewValidatedDataColumnSidecar(sidecar1);
    assertThat(fRet4.get().get()).isEqualTo(sidecar1);

    assertThat(custody.pendingRequests).isEmpty();
  }
}
