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

    public Job(int idx, List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature) {
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
