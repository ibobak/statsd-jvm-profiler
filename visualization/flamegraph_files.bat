FOR %%i IN (output\*.txt) DO  python filterlines.py -f filter.txt %%i |  perl flamegraph.pl > %%i.svg
