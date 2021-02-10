package tech.pegasys.teku.ssz.backing.schema;

import java.util.List;
import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszMutableComposite;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public interface SszCollectionSchema<
    SszElementT extends SszData, SszCollectionT extends SszCollection<SszElementT>>
    extends SszCompositeSchema<SszCollectionT> {

  SszSchema<SszElementT> getElementSchema();

  @SuppressWarnings("unchecked")
  default SszCollectionT createFromElements(List<SszElementT> elements) {
    SszMutableComposite<SszElementT> writableCopy = getDefault().createWritableCopy();
    int idx = 0;
    for (SszElementT element : elements) {
      writableCopy.set(idx++, element);
    }
    return (SszCollectionT) writableCopy.commitChanges();
  }

  default TreeNode createTreeFromElements(List<SszElementT> elements) {
    // TODO: suboptimal
    return createFromElements(elements).getBackingNode();
  }
}
