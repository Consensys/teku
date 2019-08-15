package tech.pegasys.artemis.datastructures.state;

import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.reflectionInformation.ReflectionInformation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BouncyCastleExtension.class)
public class HistoricalBatchTest {

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths = List.of(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.SLOTS_PER_HISTORICAL_ROOT);
    assertEquals(vectorLengths, SimpleOffsetSerializer.classReflectionInfo.get(HistoricalBatch.class).getVectorLengths());
  }
}
