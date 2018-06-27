#!/bin/bash
FILES=output/cpu*.txt
for f in $FILES
do
    echo "Processing $f.svg file..."
    ./filterlines.py -f filter.txt $f | perl flamegraph.pl > $f.svg
done
