package tech.pegasys.artemis.storage.serializers;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import tech.pegasys.artemis.storage.LatestMessage;

public class LatestMessageSerializer implements Serializer<LatestMessage> {

  @Override
  public void serialize(final DataOutput2 out, final LatestMessage value) throws IOException {
    out.writeLong(value.getEpoch().longValue());
    out.writeChars(value.getRoot().toHexString());
  }

  @Override
  public LatestMessage deserialize(final DataInput2 input, final int available) throws IOException {
    return new LatestMessage(
        UnsignedLong.fromLongBits(input.readLong()), Bytes32.fromHexString(input.readLine()));
  }
}
