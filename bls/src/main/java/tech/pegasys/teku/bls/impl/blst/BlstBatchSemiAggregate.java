package tech.pegasys.teku.bls.impl.blst;

import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.pairing;

public final class BlstBatchSemiAggregate implements BatchSemiAggregate {
  private final pairing ctx;
  private boolean released = false;

  BlstBatchSemiAggregate(pairing ctx) {
    this.ctx = ctx;
  }

  pairing getCtx() {
    return ctx;
  }

  void release() {
    if (released)
      throw new IllegalStateException("Attempting to use disposed BatchSemiAggregate");
    released = true;
    ctx.delete();
  }

  void mergeWith(BlstBatchSemiAggregate other) {
    BLST_ERROR ret = blst.pairing_merge(getCtx(), other.getCtx());
    if (ret != BLST_ERROR.BLST_SUCCESS) {
      throw new IllegalStateException("Error merging Blst pairing contexts: " + ret);
    }
  }
}
