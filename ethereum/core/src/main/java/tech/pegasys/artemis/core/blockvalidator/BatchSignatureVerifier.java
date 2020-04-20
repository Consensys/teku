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
    final List<BLSPublicKey> publicKeys;
    final Bytes message;
    final BLSSignature signature;

    public Job(List<BLSPublicKey> publicKeys, Bytes message,
        BLSSignature signature) {
      this.publicKeys = publicKeys;
      this.message = message;
      this.signature = signature;
    }
  }

  private final List<Job> toVerify = new ArrayList<>();

  @Override
  public boolean verify(List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature) {
    toVerify.add(new Job(publicKeys, message, signature));
    return true;
  }

  public boolean batchVerify() {
    List<BatchSemiAggregate> batchSemiAggregates = toVerify.stream()
        .parallel()
        .map(job -> BLS.prepareBatchVerify(job.publicKeys, job.message, job.signature))
        .collect(Collectors.toList());
    return BLS.completeBatchVerify(batchSemiAggregates);
  }
}
