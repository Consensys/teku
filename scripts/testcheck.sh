#!/bin/bash
set -euo pipefail

#
# The project directory. With this, we can run this script from anywhere.
#
TEKU_DIR=$(cd $(dirname "${BASH_SOURCE[0]}")/.. && pwd)

#
# These two arrays are used like a map.
#
TEST_TYPE=(
  "test"
  "integration-test"
  "property-test"
)
TEST_SUFFIX=(
  "Test.java"
  "IntegrationTest.java"
  "PropertyTest.java"
)

#
# Will be overwritten if there's an error.
#
EXIT_VALUE=0

#
# For each test file, check that the corresponding main file exists.
#
for (( i=0; i<${#TEST_TYPE[@]}; i++ )); do
  for testdir in $(find $TEKU_DIR | grep "/src/${TEST_TYPE[$i]}$"); do
    maindir=${testdir/src\/${TEST_TYPE[$i]}/src\/main}

    #
    # If the directory doesn't exist, it's probably a collection of
    # integration tests.
    #
    if [ ! -d $maindir ]; then
      continue
    fi

    for testfile in $(find $testdir | grep "${TEST_SUFFIX[$i]}$"); do
      mainfile=${testfile/$testdir/$maindir}
      mainfile=${mainfile/%${TEST_SUFFIX[$i]}/.java}

      #
      # If the main file does not exist, let's try to find that file
      # somewhere in the same project source tree.
      #
      if [ ! -f $mainfile ]; then
        mainfilename=$(basename $mainfile)
        for file in $(find $maindir | grep "/$mainfilename"); do
          testfile_dircount=$(echo $testfile | grep -o "/" | wc -l)
          file_dircount=$(echo $file | grep -o "/" | wc -l)

          #
          # Filter out findings that do not have the same number of
          # directories. This helps identify obviously incorrect paths;
          # the test file is probably in the wrong directory.
          #
          if [ "$file_dircount" == "$testfile_dircount" ]; then
            printf "\nTest may exist in the wrong directory:\n"
            printf "  Main: %s\n" $(realpath --relative-to=$TEKU_DIR $file)
            printf "  Test: %s\n" $(realpath --relative-to=$TEKU_DIR $testfile)

            newtestfile=${file/$maindir/$testdir}
            newtestfile=${newtestfile/%.java/${TEST_SUFFIX[$i]}}
            printf "Maybe test should be here:\n"
            printf "  Test: %s\n" $(realpath --canonicalize-missing \
              --relative-to=$TEKU_DIR $newtestfile)

            EXIT_VALUE=1
          fi
        done
      fi
    done
  done
done

exit $EXIT_VALUE