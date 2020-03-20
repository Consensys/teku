package tech.pegasys.artemis.datastructures.state;

import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.view.ContainerViewWriteImpl;
import tech.pegasys.artemis.util.cache.Cache;

class MutableValidatorImpl extends ContainerViewWriteImpl implements MutableValidator {

  MutableValidatorImpl(ValidatorImpl backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected ValidatorImpl createViewRead(TreeNode backingNode, Cache<Integer, ViewRead> viewCache) {
    return new ValidatorImpl(backingNode, viewCache);
  }

  @Override
  public Validator commitChanges() {
    return (Validator) super.commitChanges();
  }

  @Override
  public MutableValidator createWritableCopy() {
    return (MutableValidator) super.createWritableCopy();
  }

  @Override
  public int getSSZFieldCount() {
    throw new UnsupportedOperationException("Only immutable view supports serialization for now");
  }
}
