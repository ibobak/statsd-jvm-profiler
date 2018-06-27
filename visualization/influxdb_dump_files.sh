# -o servername
# -u user
# -p password
# -d database 
# -e tagvalues through comma
# -t tagnames through comma
# -x output directory 
# -f file with strings to not include 
# -s algorithm_of_line_numbering:  0 = methodname:linenumber;  1 = linenumber:methodname,  2=methodnameonly

/opt/conda/envs/py27/bin/python influxdb_dump.py -o "10.4.12.36" -u profiler -p profiler -d profiler -e agg -t env -x "output" -f "filter.txt" -s 1


