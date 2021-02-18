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

package tech.pegasys.teku.bls.impl.blst;

import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.pairing;

final class BlstFiniteSemiAggregate implements BatchSemiAggregate {

  public static BatchSemiAggregate merge(BatchSemiAggregate agg1, BatchSemiAggregate agg2) {
    if (agg1 instanceof BlstFiniteSemiAggregate) {
      if (agg2 instanceof BlstFiniteSemiAggregate) {
        ((BlstFiniteSemiAggregate) agg1).mergeWith((BlstFiniteSemiAggregate) agg2);
        ((BlstFiniteSemiAggregate) agg2).release();
        return agg1;
      } else {
        if (((BlstInfiniteSemiAggregate) agg2).isValid()) {
          return agg1;
        } else {
          ((BlstFiniteSemiAggregate) agg1).release();
          return agg2;
        }
      }
    } else {
      if (((BlstInfiniteSemiAggregate) agg1).isValid()) {
        return agg2;
      } else {
        if (agg2 instanceof BlstFiniteSemiAggregate) {
          ((BlstFiniteSemiAggregate) agg2).release();
        }
        return agg1;
      }
    }
  }

  private final pairing ctx;
  private boolean released = false;

  BlstFiniteSemiAggregate(pairing ctx) {
    this.ctx = ctx;
  }

  pairing getCtx() {
    if (released) throw new IllegalStateException("Attempting to use disposed BatchSemiAggregate");
    return ctx;
  }

  void release() {
    if (released) throw new IllegalStateException("Attempting to use disposed BatchSemiAggregate");
    released = true;
    ctx.delete();
  }

  void mergeWith(BlstFiniteSemiAggregate other) {
    BLST_ERROR ret = blst.pairing_merge(getCtx(), other.getCtx());
    if (ret != BLST_ERROR.BLST_SUCCESS) {
      throw new IllegalStateException("Error merging Blst pairing contexts: " + ret);
    }
  }
}
