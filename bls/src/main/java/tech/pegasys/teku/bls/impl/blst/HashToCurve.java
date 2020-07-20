package tech.pegasys.teku.bls.impl.blst;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p2;

class HashToCurve {
  // The ciphersuite defined in the Eth2 specification which also serves as domain separation tag
  // https://github.com/ethereum/eth2.0-specs/blob/v0.12.0/specs/phase0/beacon-chain.md#bls-signatures
  static final Bytes ETH2_DST = Bytes.EMPTY;
//      Bytes.wrap("BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_POP_".getBytes(StandardCharsets.US_ASCII));

  static p2 hashToG2(Bytes message) {
    return hashToG2(message, ETH2_DST);
  }

  static p2 hashToG2(Bytes message, Bytes dst) {
    p2 p2Hash = new p2();
    blst.hash_to_g2(p2Hash, message.toArray(), dst.toArray(), new byte[0]);
    return p2Hash;
  }
}