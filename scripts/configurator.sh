#!/bin/bash
if [[ "$#" -ne 3 ]]
then
  echo "Usage: sh configurator.sh CONFIG_PATH VARIABLE_NAME VARIABLE_VALUE"
  exit 1
fi


sed -i.bak "s!#*.*$2:.*!$2:\ $3!g" $1
rm -f $1.bak