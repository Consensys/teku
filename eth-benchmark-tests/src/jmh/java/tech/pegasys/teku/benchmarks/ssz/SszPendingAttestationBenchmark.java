package tech.pegasys.teku.benchmarks.ssz;

import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.util.config.Constants;

public class SszPendingAttestationBenchmark extends
    SszAbstractContainerBenchmark<PendingAttestation> {

  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(1);
  private static final PendingAttestation aPendingAttestation = dataStructureUtil
      .randomPendingAttestation();

  private static final Bitlist aggregation_bits = aPendingAttestation.getAggregation_bits();
  private static final AttestationData attestationData = aPendingAttestation.getData();
  private static final UInt64 inclusion_delay = aPendingAttestation.getInclusion_delay();
  private static final UInt64 proposer_index = aPendingAttestation.getProposer_index();

  @Override
  protected PendingAttestation createContainer() {
    return new PendingAttestation(aggregation_bits,
        attestationData, inclusion_delay,
        proposer_index);
  }

//  @Override
//  protected ContainerViewType<PendingAttestation> getType() {
//    return PendingAttestation.TYPE;
//  }


  @Override
  protected Class<PendingAttestation> getContainerClass() {
    return PendingAttestation.class;
  }

  @Override
  protected void iterateData(PendingAttestation pa, Blackhole bh) {
    bh.consume(pa.getAggregation_bits());
    bh.consume(pa.getInclusion_delay());
    bh.consume(pa.getProposer_index());
    iterateData(pa.getData(), bh);
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
    new SszPendingAttestationBenchmark().customRun(5, 10000);
  }
}
