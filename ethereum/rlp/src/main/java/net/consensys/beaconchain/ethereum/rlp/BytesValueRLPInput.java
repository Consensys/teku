package net.consensys.beaconchain.ethereum.rlp;

import net.consensys.beaconchain.util.bytes.Bytes32;
import net.consensys.beaconchain.util.bytes.BytesValue;
import net.consensys.beaconchain.util.bytes.BytesValues;

import java.math.BigInteger;

/**
 * An {@link RLPInput} that reads RLP encoded data from a {@link BytesValue}.
 */
public class BytesValueRLPInput extends AbstractRLPInput {

  // The RLP encoded data.
  private final BytesValue value;

  public BytesValueRLPInput(BytesValue value, boolean lenient) {
    super(lenient);
    this.value = value;
    init(value.size(), true);
  }

  @Override
  protected byte inputByte(long offset) {
    return value.get(Math.toIntExact(offset));
  }

  @Override
  protected BytesValue inputSlice(long offset, int length) {
    return value.slice(Math.toIntExact(offset), length);
  }

  @Override
  protected Bytes32 inputSlice32(long offset) {
    return Bytes32.wrap(value, Math.toIntExact(offset));
  }

  @Override
  protected String inputHex(long offset, int length) {
    return value.slice(Math.toIntExact(offset), length).toString().substring(2);
  }

  @Override
  protected BigInteger getUnsignedBigInteger(long offset, int length) {
    return BytesValues.asUnsignedBigInteger(value.slice(Math.toIntExact(offset), length));
  }

  @Override
  protected int getInt(long offset) {
    return value.getInt(Math.toIntExact(offset));
  }

  @Override
  protected long getLong(long offset) {
    return value.getLong(Math.toIntExact(offset));
  }

  @Override
  public BytesValue raw() {
    return value;
  }
}
