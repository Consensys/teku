#/bin/bash

# Run this script to regenerate the Uint*.java files

template_file=Uint.template
sizes='8 16 24 32 64 256'
comment="// DO NOT EDIT! This file was automatically generated from ${template_file} by ${0}"

for i in $sizes; do

    cat $template_file | sed "s/#/$i/g" | sed "s|_COMMENT_|$comment|" > Uint$i.java

done
