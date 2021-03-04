package tech.pegasys.teku.ssz.backing.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.RandomSszDataGenerator;
import tech.pegasys.teku.ssz.backing.SszListAbstractTest;
import tech.pegasys.teku.ssz.backing.SszMutableCollectionAbstractTest;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszMutablePrimitiveListAbstractTest extends SszListAbstractTest,
    SszMutableCollectionAbstractTest {

  RandomSszDataGenerator generator = new RandomSszDataGenerator();

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT, SszT>>
  void append_extendsExtendableCollection(SszMutablePrimitiveList<ElT, SszT> collection) {
    if (collection.size() < collection.getSchema().getMaxLength()) {
      // collection is extendable (List effectively)
      int origSize = collection.size();
      ElT newElement = collection.getPrimitiveElementSchema().getDefault().get();
      collection.appendElement(newElement);

      assertThat(collection.size()).isEqualTo(origSize + 1);
      assertThat(collection.get(origSize)).isEqualTo(newElement);

      SszPrimitiveCollection<ElT, SszT> immCollection = collection.commitChanges();

      assertThat(immCollection.size()).isEqualTo(origSize + 1);
      assertThat(immCollection.get(origSize)).isEqualTo(newElement);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT, SszT>>
  void appendAllElements_extendsExtendableCollection(SszMutablePrimitiveList<ElT, SszT> collection) {
    if (collection.size() < collection.getSchema().getMaxLength()) {
      // collection is extendable (List effectively)
      int origSize = collection.size();

      int appendListSize = Integer.min(3, (int) (collection.getSchema().getMaxLength() - origSize));
      List<ElT> appendList = Stream
          .generate(() -> generator.randomData(collection.getPrimitiveElementSchema()))
          .limit(appendListSize)
          .map(SszPrimitive::get)
          .collect(Collectors.toList());
      collection.appendAllElements(appendList);

      assertThat(collection.size()).isEqualTo(origSize + appendListSize);
      for (int i = 0; i < appendListSize; i++) {
        assertThat(collection.get(origSize + i)).isEqualTo(appendList.get(i));
      }
    }
  }
}
