#!/bin/bash
set -euo pipefail

#
# The project directory. With this, we can run this script from anywhere.
#
TEKU_DIR=$(cd $(dirname "${BASH_SOURCE[0]}")/.. && pwd)

#
# For each test file, check that the corresponding main file exists.
#
for testdir in $(find $TEKU_DIR | grep "/src/test$"); do
    maindir=${testdir/src\/test/src\/main}
    for testfile in $(find $testdir | grep "Test.java$"); do
        mainfile=${testfile/$testdir/$maindir}
        mainfile=${mainfile/%Test.java/.java}

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
                    rel_file=$(realpath --relative-to=$TEKU_DIR $file)
                    rel_testfile=$(realpath --relative-to=$TEKU_DIR $testfile)
                    printf "\nTest may exist in the wrong directory:\n"
                    printf "  Main: %s\n" $rel_file
                    printf "  Test: %s\n" $rel_testfile

                    rel_newtestfile=${rel_file/$maindir/$testdir}
                    rel_newtestfile=${rel_newtestfile/%.java/Test.java}
                    printf "Maybe test should be here:\n"
                    printf "  Test: %s\n" $rel_newtestfile
                fi
            done
        fi
    done
done
