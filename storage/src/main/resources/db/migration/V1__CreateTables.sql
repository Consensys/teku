-- Chain Storage
CREATE TABLE block (
  blockRoot BINARY(32) NOT NULL PRIMARY KEY,
  slot INTEGER NOT NULL,
  parentRoot BINARY(32) NOT NULL,
  finalized BOOLEAN NOT NULL,
  blobId INTEGER
);
CREATE INDEX idxBlockSlot ON block(slot);

CREATE TABLE state (
  stateRoot BINARY(32) NOT NULL PRIMARY KEY,
  blockRoot BINARY(32) NOT NULL,
  slot INTEGER NOT NULL,
  blobId INTEGER
);
CREATE INDEX idxStateBlockRootSlot ON state(blockRoot, slot);
CREATE INDEX idxStateSlot ON state(slot);

CREATE TABLE checkpoint (
  type VARCHAR(20) NOT NULL PRIMARY KEY,
  blockRoot BINARY(32) NOT NULL,
  epoch INTEGER NOT NULL
) WITHOUT ROWID;

CREATE TABLE vote (
  validatorIndex INTEGER NOT NULL PRIMARY KEY,
  currentRoot BINARY(32) NOT NULL,
  nextRoot BINARY(32) NOT NULL,
  nextEpoch INTEGER NOT NULL
);


-- ProtoArray

CREATE TABLE protoarray (
  id INT NOT NULL DEFAULT 1 PRIMARY KEY,
  justifiedEpoch INTEGER NOT NULL,
  finalizedEpoch INTEGER NOT NULL,
  blocks BINARY(100) NOT NULL
);

-- ETH1

CREATE TABLE eth1_min_genesis (
  id INT NOT NULL DEFAULT 1 PRIMARY KEY,
  block_timestamp INTEGER NOT NULL,
  block_number INTEGER NOT NULL,
  block_hash BINARY(32) NOT NULL
);

CREATE TABLE eth1_deposit_block (
  block_number INTEGER NOT NULL PRIMARY KEY,
  block_timestamp INTEGER NOT NULL,
  block_hash BINARY(32) NOT NULL
);

CREATE TABLE eth1_deposit (
  merkle_tree_index INTEGER NOT NULL PRIMARY KEY,
  block_number INTEGER NOT NULL,
  public_key BINARY(48) NOT NULL,
  withdrawal_credentials BINARY(32) NOT NULL,
  signature BINARY(96) NOT NULL,
  amount INTEGER NOT NULL
);
CREATE INDEX idxDepositBlockNumber ON eth1_deposit(block_number);