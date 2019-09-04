package tech.pegasys.artemis.storage.serializers;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class UnsignedLongSerializer implements Serializer<UnsignedLong> {

  @Override
  public void serialize(final DataOutput2 out, final UnsignedLong value) throws IOException {
    out.writeLong(value.longValue());
  }

  @Override
  public UnsignedLong deserialize(final DataInput2 input, final int available)
      throws IOException {
    return UnsignedLong.fromLongBits(input.readLong());
  }
}
