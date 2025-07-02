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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;

@SuppressWarnings({"JavaCase", "FutureReturnValueIgnored"})
public class DataColumnSidecarCustodyImplTest {

  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataColumnSidecarDB db = new DataColumnSidecarDBStub();
  final DataColumnSidecarDbAccessor dbAccessor =
      DataColumnSidecarDbAccessor.builder(db).spec(spec).build();
  final CanonicalBlockResolverStub blockResolver = new CanonicalBlockResolverStub(spec);
  final CustodyGroupCountManager custodyGroupCountManager = mock(CustodyGroupCountManager.class);

  final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  final int groupCount = config.getNumberOfCustodyGroups();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  private DataColumnSidecar createSidecar(final BeaconBlock block, final int column) {
    return dataStructureUtil.randomDataColumnSidecar(createSigned(block), UInt64.valueOf(column));
  }

  private SignedBeaconBlockHeader createSigned(final BeaconBlock block) {
    return dataStructureUtil.signedBlock(block).asHeader();
  }

  @Test
  void sanityTest() throws Throwable {
    DataColumnSidecarCustodyImpl custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            blockResolver,
            dbAccessor,
            MinCustodyPeriodSlotCalculator.createFromSpec(spec),
            custodyGroupCountManager,
            groupCount);
    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(
            List.of(UInt64.valueOf(0), UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));

    BeaconBlock block = blockResolver.addBlock(10, true);
    DataColumnSidecar sidecar0 = createSidecar(block, 0);
    DataColumnSidecar sidecar1 = createSidecar(block, 1);
    DataColumnSlotAndIdentifier columnId0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    SafeFuture<Optional<DataColumnSidecar>> fRet1 = custody.getCustodyDataColumnSidecar(columnId0);
    Optional<DataColumnSidecar> ret1 = fRet1.get(1, TimeUnit.SECONDS);

    assertThat(ret1).isEmpty();

    custody.onNewValidatedDataColumnSidecar(sidecar1);
    custody.onNewValidatedDataColumnSidecar(sidecar0);

    SafeFuture<Optional<DataColumnSidecar>> fRet2_1 =
        custody.getCustodyDataColumnSidecar(columnId0);
    SafeFuture<Optional<DataColumnSidecar>> fRet2_2 =
        custody.getCustodyDataColumnSidecar(columnId0);

    assertThat(fRet2_1.get().get()).isEqualTo(sidecar0);
    assertThat(fRet2_2.get().get()).isEqualTo(sidecar0);
  }
}
