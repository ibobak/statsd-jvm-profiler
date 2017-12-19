# -o servername
# -u user
# -p password
# -d database 
# -e tagvalues through comma
# -t tagnames through comma
# -x output directory 
# -f file with strings to not include 
# -s algorithm_of_line_numbering:  0 = methodname:linenumber;  1 = linenumber:methodname,  2=methodnameonly

python influxdb_dump.py -o "192.168.56.103" -u profiler -p profiler -d local -e bigdata -t prefix -x "output" -f "filter.txt" -s 1


