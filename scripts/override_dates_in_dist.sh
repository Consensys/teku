#!/bin/bash
set -euo pipefail

DIST_FILE_NAME=$(basename $1)
DIST_FILE_ABSPATH=$(readlink -f $1)

#
# The timestamp of the last commit. We need to remove the last part of the
# timestamp because the 'touch' command doesn't work with it.
#
LAST_COMMIT_DATETIME=$(git log -1 --format=%cI | cut -d'+' -f1)

#
# This function will be called as the script is exiting. It will delete the
# temporary directory that we created.
#
TEMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf $TEMP_DIR
}
trap cleanup exit

#
# Unpack the distributions.
#
pushd $TEMP_DIR >/dev/null
if [[ "$DIST_FILE_NAME" == *.zip ]]; then
  unzip -q $DIST_FILE_ABSPATH
else
  tar xzf $DIST_FILE_ABSPATH
fi

#
# For each file in the distribution, override the modified date.
#
find . -exec touch -d $LAST_COMMIT_DATETIME {} \;

#
# Repack the distribution.
#
if [[ "$DIST_FILE_NAME" == *.zip ]]; then
  zip -q -r $DIST_FILE_NAME $(ls .)
else
  tar czf $DIST_FILE_NAME $(ls .)
fi

#
# Replace the original distribution with the new version.
#
cp $DIST_FILE_NAME $DIST_FILE_ABSPATH
popd >/dev/null
