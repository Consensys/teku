package tech.pegasys.teku.bls.impl.blst;

import tech.pegasys.teku.bls.BatchSemiAggregate;

final class BlstInfiniteSemiAggregate implements BatchSemiAggregate {
  private final boolean isValid;

  public BlstInfiniteSemiAggregate(boolean isValid) {
    this.isValid = isValid;
  }

  public boolean isValid() {
    return isValid;
  }
}
