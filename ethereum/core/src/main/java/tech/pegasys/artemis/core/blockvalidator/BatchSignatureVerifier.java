package tech.pegasys.artemis.core.blockvalidator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.bls.BLSSignatureVerifier;
import tech.pegasys.artemis.bls.mikuli.BLS12381.BatchSemiAggregate;

public class BatchSignatureVerifier implements BLSSignatureVerifier {
  private static class Job {
    final int idx;
    final List<BLSPublicKey> publicKeys;
    final Bytes message;
    final BLSSignature signature;

    public Job(int idx, List<BLSPublicKey> publicKeys, Bytes message,
        BLSSignature signature) {
      this.idx = idx;
      this.publicKeys = publicKeys;
      this.message = message;
      this.signature = signature;
    }
  }

  private final List<Job> toVerify = new ArrayList<>();
  private boolean complete = false;

  @Override
  public boolean verify(List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature) {
    if (complete) throw new IllegalStateException("Reuse of disposable instance");
    toVerify.add(new Job(toVerify.size(), publicKeys, message, signature));
    return true;
  }

  public boolean batchVerify() {
    if (complete) throw new IllegalStateException("Reuse of disposable instance");
    List<BatchSemiAggregate> batchSemiAggregates =
        toVerify.stream()
            .parallel()
            .map(job -> BLS.prepareBatchVerify(job.idx, job.publicKeys, job.message, job.signature))
            .collect(Collectors.toList());
    complete = true;
    return BLS.completeBatchVerify(batchSemiAggregates);
  }
}
