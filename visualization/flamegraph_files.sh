#!/bin/bash
FILES=output/cpu*.txt
for f in $FILES
do
    echo "Processing $f.svg file..."
    /opt/conda/envs/py27/bin/python filterlines.py -f filter.txt $f | perl flamegraph.pl > $f.svg
done
