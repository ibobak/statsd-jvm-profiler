FOR %%i IN (output\cpu*.txt) DO  python filterlines.py -f filter.txt %%i |  perl flamegraph.pl > %%i.svg
