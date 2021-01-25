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

public class SszBenchUtil {

  public static void iterateData(PendingAttestation pa, Blackhole bh) {
    bh.consume(pa.getAggregation_bits());
    bh.consume(pa.getInclusion_delay());
    bh.consume(pa.getProposer_index());
    iterateData(pa.getData(), bh);
  }

  public static void iterateData(BeaconBlockBody bbb, Blackhole bh) {
    bh.consume(bbb.getRandao_reveal());
    iterateData(bbb.getEth1_data(), bh);
    bh.consume(bbb.getGraffiti());
    bbb.getProposer_slashings().forEach(s -> iterateData(s, bh));
    bbb.getAttester_slashings().forEach(s -> iterateData(s, bh));
    bbb.getAttestations().forEach(a -> iterateData(a, bh));
    bbb.getDeposits().forEach(d -> iterateData(d, bh));
    bbb.getVoluntary_exits().forEach(d -> iterateData(d, bh));
  }

  public static void iterateData(SignedVoluntaryExit d, Blackhole bh) {
    bh.consume(d.getSignature());
    iterateData(d.getMessage(), bh);
  }

  public static void iterateData(VoluntaryExit message, Blackhole bh) {
    bh.consume(message.getEpoch());
    bh.consume(message.getValidator_index());
  }

  public static void iterateData(Deposit d, Blackhole bh) {
    d.getProof().forEach(bh::consume);
    iterateData(d.getData(), bh);
  }

  public static void iterateData(DepositData data, Blackhole bh) {
    bh.consume(data.getPubkey());
    bh.consume(data.getWithdrawal_credentials());
    bh.consume(data.getAmount());
    bh.consume(data.getSignature());
  }

  public static void iterateData(Attestation a, Blackhole bh) {
    bh.consume(a.getAggregation_bits());
    iterateData(a.getData(), bh);
    bh.consume(a.getAggregate_signature());
  }

  public static void iterateData(AttesterSlashing s, Blackhole bh) {
    iterateData(s.getAttestation_1(), bh);
    iterateData(s.getAttestation_2(), bh);
  }

  public static void iterateData(IndexedAttestation a, Blackhole bh) {
    a.getAttesting_indices().forEach(bh::consume);
    bh.consume(a.getSignature());
    iterateData(a.getData(), bh);
  }

  public static void iterateData(ProposerSlashing s, Blackhole bh) {
    iterateData(s.getHeader_1(), bh);
    iterateData(s.getHeader_2(), bh);
  }

  public static void iterateData(SignedBeaconBlockHeader h, Blackhole bh) {
    bh.consume(h.getSignature());
    iterateData(h.getMessage(), bh);
  }

  public static void iterateData(BeaconBlockHeader h, Blackhole bh) {
    bh.consume(h.getSlot());
    bh.consume(h.getProposerIndex());
    bh.consume(h.getParentRoot());
    bh.consume(h.getBodyRoot());
    bh.consume(h.getStateRoot());
  }

  public static void iterateData(Eth1Data ed, Blackhole bh) {
    bh.consume(ed.getDeposit_root());
    bh.consume(ed.getDeposit_count());
    bh.consume(ed.getBlock_hash());
  }

  public static void iterateData(AttestationData ad, Blackhole bh) {
    bh.consume(ad.getSlot());
    bh.consume(ad.getIndex());
    bh.consume(ad.getEarliestSlotForForkChoice());
    iterateData(ad.getSource(), bh);
    iterateData(ad.getTarget(), bh);
  }

  public static void iterateData(Checkpoint cp, Blackhole bh) {
    bh.consume(cp.getEpoch());
    bh.consume(cp.getRoot());
  }
}
