package tech.pegasys.teku.spec.propertytest.suppliers.type;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.propertytest.suppliers.execution.versions.eip4844.BlobBytesSupplier;

/**
 * An implementation of {@link ArbitrarySupplier<Bytes>} which chooses randomly between {@link
 * BlobBytesSupplier} and {@link BytesSupplier}
 */
public class DiverseBlobBytesSupplier extends BlobBytesSupplier {

  @Override
  public Arbitrary<Bytes> get() {
    final Arbitrary<Bytes> randomSizeBlob = new BytesSupplier().get();
    return Arbitraries.oneOf(List.of(super.get(), randomSizeBlob));
  }
}
