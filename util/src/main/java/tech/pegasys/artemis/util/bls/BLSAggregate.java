package tech.pegasys.artemis.util.bls;

import net.consensys.cava.bytes.Bytes48;

import java.util.ArrayList;

public class BLSAggregate {

  public static Bytes48 bls_aggregate_pubkeys(ArrayList<Bytes48> pubkeys) {
    // todo
    return Bytes48.ZERO;
  }

  public static Bytes48[] bls_aggregate_signatures(ArrayList<Bytes48> signatures) {
    // todo
    return new Bytes48[]{Bytes48.ZERO};
  }

}
