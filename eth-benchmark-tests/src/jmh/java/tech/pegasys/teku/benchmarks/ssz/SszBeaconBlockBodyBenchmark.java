package tech.pegasys.teku.benchmarks.ssz;

import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;

public class SszBeaconBlockBodyBenchmark extends
    SszAbstractContainerBenchmark<BeaconBlockBody> {

  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(1);
  private static final BeaconBlockBody beaconBlockBody = dataStructureUtil.randomBeaconBlockBody();

  @Override
  protected BeaconBlockBody createContainer() {
    return new BeaconBlockBody(beaconBlockBody.getRandao_reveal(), beaconBlockBody.getEth1_data(),
        beaconBlockBody.getGraffiti(), beaconBlockBody.getProposer_slashings(),
        beaconBlockBody.getAttester_slashings(), beaconBlockBody.getAttestations(),
        beaconBlockBody.getDeposits(), beaconBlockBody.getVoluntary_exits());
  }

  @Override
  protected Class<BeaconBlockBody> getContainerClass() {
    return BeaconBlockBody.class;
  }

  @Override
  protected void iterateData(BeaconBlockBody bbb, Blackhole bh) {
    bh.consume(bbb.getRandao_reveal());
    iterateData(bbb.getEth1_data(), bh);
    bh.consume(bbb.getGraffiti());
    bbb.getProposer_slashings().forEach(s -> iterateData(s, bh));
    bbb.getAttester_slashings().forEach(s -> iterateData(s, bh));
    bbb.getAttestations().forEach(a -> iterateData(a, bh));
    bbb.getDeposits().forEach(d -> iterateData(d, bh));
    bbb.getVoluntary_exits().forEach(d -> iterateData(d, bh));
  }

  private void iterateData(SignedVoluntaryExit d, Blackhole bh) {
    bh.consume(d.getSignature());
    iterateData(d.getMessage(), bh);
  }

  private void iterateData(VoluntaryExit message, Blackhole bh) {
    bh.consume(message.getEpoch());
    bh.consume(message.getValidator_index());
  }

  private void iterateData(Deposit d, Blackhole bh) {
    d.getProof().forEach(bh::consume);
    iterateData(d.getData(), bh);
  }

  private void iterateData(DepositData data, Blackhole bh) {
    bh.consume(data.getPubkey());
    bh.consume(data.getWithdrawal_credentials());
    bh.consume(data.getAmount());
    bh.consume(data.getSignature());
  }

  private void iterateData(Attestation a, Blackhole bh) {
    bh.consume(a.getAggregation_bits());
    iterateData(a.getData(), bh);
    bh.consume(a.getAggregate_signature());
  }

  private void iterateData(AttesterSlashing s, Blackhole bh) {
    iterateData(s.getAttestation_1(), bh);
    iterateData(s.getAttestation_2(), bh);
  }

  private void iterateData(IndexedAttestation a, Blackhole bh) {
    a.getAttesting_indices().forEach(bh::consume);
    bh.consume(a.getSignature());
    iterateData(a.getData(), bh);
  }

  private void iterateData(ProposerSlashing s, Blackhole bh) {
    iterateData(s.getHeader_1(), bh);
    iterateData(s.getHeader_2(), bh);
  }

  private void iterateData(SignedBeaconBlockHeader h, Blackhole bh) {
    bh.consume(h.getSignature());
    iterateData(h.getMessage(), bh);
  }

  private void iterateData(BeaconBlockHeader h, Blackhole bh) {
    bh.consume(h.getSlot());
    bh.consume(h.getProposerIndex());
    bh.consume(h.getParentRoot());
    bh.consume(h.getBodyRoot());
    bh.consume(h.getStateRoot());
  }

  private void iterateData(Eth1Data ed, Blackhole bh) {
    bh.consume(ed.getDeposit_root());
    bh.consume(ed.getDeposit_count());
    bh.consume(ed.getBlock_hash());
  }

  private void iterateData(AttestationData ad, Blackhole bh) {
    bh.consume(ad.getSlot());
    bh.consume(ad.getIndex());
    bh.consume(ad.getEarliestSlotForForkChoice());
    iterateData(ad.getSource(), bh);
    iterateData(ad.getTarget(), bh);
  }

  private void iterateData(Checkpoint cp, Blackhole bh) {
    bh.consume(cp.getEpoch());
    bh.consume(cp.getRoot());
  }

  public static void main(String[] args) {
    new SszBeaconBlockBodyBenchmark().customRun(5, 10000);
  }
}
