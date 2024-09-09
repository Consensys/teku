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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

@SuppressWarnings("unused")
public class DasCustodyStand {

  public static Builder builder(Spec spec) {
    return new Builder().withSpec(spec);
  }

  public final Spec spec;

  public final CanonicalBlockResolverStub blockResolver;
  public final UInt256 myNodeId;

  public final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;
  public final DataColumnSidecarDBStub db;
  public final DataColumnSidecarDbAccessor dbAccessor;

  public final SpecConfigEip7594 config;
  public final DataColumnSidecarCustodyImpl custody;

  public final DataStructureUtil dataStructureUtil;

  private final List<SlotEventsChannel> slotListeners = new CopyOnWriteArrayList<>();
  private final List<FinalizedCheckpointChannel> finalizedListeners = new CopyOnWriteArrayList<>();
  private final int totalCustodySubnetCount;

  private UInt64 currentSlot = UInt64.ZERO;

  public DasCustodyStand(
      Spec spec, UInt64 currentSlot, UInt256 myNodeId, int totalCustodySubnetCount) {
    this.spec = spec;
    this.myNodeId = myNodeId;
    this.blockResolver = new CanonicalBlockResolverStub(spec);
    this.config = SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
    this.minCustodyPeriodSlotCalculator = MinCustodyPeriodSlotCalculator.createFromSpec(spec);
    this.db = new DataColumnSidecarDBStub();
    this.dbAccessor = DataColumnSidecarDbAccessor.builder(db).spec(spec).build();

    this.custody =
        new DataColumnSidecarCustodyImpl(
            spec,
            blockResolver,
            dbAccessor,
            minCustodyPeriodSlotCalculator,
            myNodeId,
            totalCustodySubnetCount);
    subscribeToSlotEvents(this.custody);
    subscribeToFinalizedEvents(this.custody);

    DataStructureUtil util = new DataStructureUtil(0, spec);
    BLSSignature singleSignature = util.randomSignature();
    BLSPublicKey singlePubKey = util.randomPublicKey();
    this.dataStructureUtil =
        util.withSignatureGenerator(__ -> singleSignature).withPubKeyGenerator(() -> singlePubKey);
    this.totalCustodySubnetCount = totalCustodySubnetCount;
  }

  public SignedBeaconBlock createBlockWithBlobs(int slot) {
    return createBlock(slot, 3);
  }

  public SignedBeaconBlock createBlockWithoutBlobs(int slot) {
    return createBlock(slot, 0);
  }

  public SignedBeaconBlock createBlock(int slot, int blobCount) {
    UInt64 slotU = UInt64.valueOf(slot);
    BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(blobCount);
    BeaconBlock block = dataStructureUtil.randomBeaconBlock(slotU, beaconBlockBody);
    return dataStructureUtil.signedBlock(block);
  }

  public DataColumnSidecar createSidecar(SignedBeaconBlock block, int column) {
    return dataStructureUtil.randomDataColumnSidecar(block.asHeader(), UInt64.valueOf(column));
  }

  public boolean hasBlobs(BeaconBlock block) {
    return block
        .getBody()
        .toVersionEip7594()
        .map(b -> !b.getBlobKzgCommitments().isEmpty())
        .orElse(false);
  }

  public Collection<UInt64> getCustodyColumnIndexes(UInt64 slot) {
    UInt64 epoch = spec.computeEpochAtSlot(slot);
    return spec.atEpoch(epoch)
        .miscHelpers()
        .toVersionEip7594()
        .map(
            miscHelpersEip7594 ->
                miscHelpersEip7594.computeCustodyColumnIndexes(myNodeId, totalCustodySubnetCount))
        .orElse(Collections.emptyList());
  }

  public UInt64 getMinCustodySlot() {
    return minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(currentSlot);
  }

  public List<DataColumnSidecar> createCustodyColumnSidecars(SignedBeaconBlock block) {
    if (hasBlobs(block.getBeaconBlock().orElseThrow())) {
      Collection<UInt64> custodyColumnIndexes = getCustodyColumnIndexes(block.getSlot());
      return custodyColumnIndexes.stream()
          .map(colIndex -> createSidecar(block, colIndex.intValue()))
          .toList();
    } else {
      return Collections.emptyList();
    }
  }

  public void subscribeToSlotEvents(SlotEventsChannel subscriber) {
    slotListeners.add(subscriber);
  }

  public void incCurrentSlot(int delta) {
    setCurrentSlot(getCurrentSlot().intValue() + delta);
  }

  public void setCurrentSlot(int slot) {
    if (currentSlot.isGreaterThan(slot)) {
      throw new IllegalArgumentException("New slot " + slot + " < " + currentSlot);
    }
    currentSlot = UInt64.valueOf(slot);
    slotListeners.forEach(l -> l.onSlot(UInt64.valueOf(slot)));
  }

  public UInt64 getCurrentSlot() {
    return currentSlot;
  }

  public void subscribeToFinalizedEvents(FinalizedCheckpointChannel subscriber) {
    finalizedListeners.add(subscriber);
  }

  public void setFinalizedEpoch(int epoch) {
    Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.valueOf(epoch), Bytes32.ZERO);
    finalizedListeners.forEach(l -> l.onNewFinalizedCheckpoint(finalizedCheckpoint, false));
  }

  public static class Builder {
    private Spec spec;
    private UInt64 currentSlot = UInt64.ZERO;
    private UInt256 myNodeId = UInt256.ONE;
    private Integer totalCustodySubnetCount;

    public Builder withSpec(Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder withCurrentSlot(UInt64 currentSlot) {
      this.currentSlot = currentSlot;
      return this;
    }

    public Builder withMyNodeId(UInt256 myNodeId) {
      this.myNodeId = myNodeId;
      return this;
    }

    public Builder withTotalCustodySubnetCount(Integer totalCustodySubnetCount) {
      this.totalCustodySubnetCount = totalCustodySubnetCount;
      return this;
    }

    public DasCustodyStand build() {
      if (totalCustodySubnetCount == null) {
        checkNotNull(spec);
        SpecConfigEip7594 configEip7594 =
            SpecConfigEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).getConfig());
        totalCustodySubnetCount = configEip7594.getCustodyRequirement();
      }
      return new DasCustodyStand(spec, currentSlot, myNodeId, totalCustodySubnetCount);
    }
  }
}
