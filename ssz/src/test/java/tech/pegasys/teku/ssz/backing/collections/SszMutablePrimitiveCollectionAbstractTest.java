package tech.pegasys.teku.ssz.backing.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszDataAssert;
import tech.pegasys.teku.ssz.backing.SszMutableCollectionAbstractTest;
import tech.pegasys.teku.ssz.backing.SszMutableComposite;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszMutablePrimitiveCollectionAbstractTest extends SszPrimitiveCollectionAbstractTest,
    SszMutableCollectionAbstractTest {

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT, SszT>>
  void setElement_throwsIndexOutOfBounds(SszMutablePrimitiveCollection<ElT, SszT> collection) {
    assertThatThrownBy(() -> collection
        .setElement(collection.size() + 1, collection.getPrimitiveElementSchema().getDefault()
            .get())).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT, SszT>>
  void setElement_extendsExtendableCollection(SszMutablePrimitiveCollection<ElT, SszT> collection) {
    if (collection.size() < collection.getSchema().getMaxLength()) {
      // collection is extendable (List effectively)
      int origSize = collection.size();
      ElT newElement = collection.getPrimitiveElementSchema().getDefault().get();
      collection.setElement(collection.size(), newElement);

      assertThat(collection.size()).isEqualTo(origSize + 1);
      assertThat(collection.get(origSize)).isEqualTo(newElement);

      SszPrimitiveCollection<ElT, SszT> immCollection = collection.commitChanges();

      assertThat(immCollection.size()).isEqualTo(origSize + 1);
      assertThat(immCollection.get(origSize)).isEqualTo(newElement);
    }
  }
}
