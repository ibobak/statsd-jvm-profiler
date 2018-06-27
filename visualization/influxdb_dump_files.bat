rem -o servername
rem -u user
rem -p password
rem -d database 
rem -e tagvalues through comma
rem -t tagnames through comma
rem -x output directory 
rem -f file with strings to not include 
rem -s algorithm_of_line_numbering:  0 = methodname:linenumber;  1 = linenumber:methodname,  2=methodnameonly

python influxdb_dump.py -o "192.168.56.103" -u profiler -p profiler -d profilerqa -e bigdata -t prefix -x "output" -f "filter.txt" -s 1


