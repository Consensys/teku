package tech.pegasys.artemis.storage.serializers;

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class Bytes32Serializer implements Serializer<Bytes32> {

  @Override
  public void serialize(final DataOutput2 out, final Bytes32 value) throws IOException {
    out.writeChars(value.toHexString());
  }

  @Override
  public Bytes32 deserialize(final DataInput2 input, final int available) throws IOException {
    return Bytes32.fromHexString(input.readLine());
  }
}
