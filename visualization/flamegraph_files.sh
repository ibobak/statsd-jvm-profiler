#!/bin/bash
FILES=tr_*
for f in $FILES
do
    echo "Processing $f.svg file..."
    ./filterlines.py -f filter.txt $f | perl flamegraph.pl > $f.svg
done
