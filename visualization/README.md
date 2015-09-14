# Additions by Ihor Bobak

## influxdb_dump.py

This script was rewritten by me almost completely, therefore I am writing here a new descriptin of how it works now.

The list of options:

Option | Meaning
-------|--------
-o     | Hostname where InfluxDB is running (required)
-r     | Port for the InfluxDB HTTP API (optional, defaults to 8086)
-u     | Username to use when connecting to InfluxDB (required)
-p     | Password to use when connection to InfluxDB (required)
-d     | Database containing the profiler metrics (required)
-e     | List of tag values
-t     | List of tag names
-x     | The output directory for the generated files
-f     | File with filtering lines, i.e. if any of the lines (from this file) is met in the stacktrace - it won't go to the output.
-s     | Sort order of methods. 0 means "sort by methodname:linenumber", 1 means "sort by linenumber:methodname", 2 means "output only method names". 0 is default.


Example how it can be launched:
```
influxdb_dump.py -o "192.168.56.103" -u profiler -p profiler -d profilerqa -e bigdata.Ihor_Bobak.YARN.2.all.all -t prefix.username.job.flow.stage.phase -x "output" -f "filter.txt" -s 1
```

You can also see the file influxdb_dump_files.bat (Windows) or influxdb_dump_files.sh (Linux) where you can put your parameters.

Explanation of the command line shown above:

it will connect to the InfluxDB server 192.168.56.103 under username "profiler" and the same password, database name = "profilerqa",  the prefix will be parsed as:
```
prefix=bigdata
username=Ihor_Bobak
job=YARN
flow=2
stage=all
phase=all
```

these tags will be sent in the "where" condition of the query to the InfluxDB database. Thus, -e and -t parameters are complementary:  -t are tag names, -e are tag values.

After the results are be fetched from the InfluxDB, they will be filtered: the rows which contain ANY substring from filter.txt file will NOT be placed into the output txt file. Parameter -f is optional: if you don't specify it, it will output all rows.  

-x parameter defines the output directory. After you run the influxdb_dump.py script, you will get this set of files: 

```
all_2015_07_28_11_59_00_409.txt 
host_2015_07_28_11_59_00_409__vin-h5.poc-vin.cloud.company.com.txt 
jvm_2015_07_28_11_59_00_409__vin-h5.poc-vin.cloud.company.com_26802.txt 
```

The file with "all" prefix is for all the stacktraces running in the cluster.
The set of files with "host" prefix is a cumulative flame graph for a specified host.
The set of files with "jvm" prefix are files for each jvm.


Later on, on this set of files you can launch the command which will convert the text into SVG using the [FlameGraph library by Brendan Gregg](https://github.com/brendangregg/FlameGraph)

```
#!/bin/bash
FILES=output/*.txt
for f in $FILES
do
    echo "Processing $f.svg file..."
    ./filterlines.py -f filter.txt $f | perl flamegraph.pl > $f.svg
done
```

You may use flamegraph_files.bat (Windows) or flamegraph_files.sh (Linux) for this purpose.

Note: there is one more little file - filterlines.py - which does the filtering as well, but it filters the text file. 


## call_tree.py

This script can transform the result obtained from  influxdb_dump.py into a text file with a hierarchical output of the called methods.

E.g. if you have some stacktraces:
```
A->B->C
A->B->C->D
A->B->E
A->X->Y
```

which are notated in the output from influxdb_dump.py as
```
A;B;C 1
A;B;C;D 1
A;B;E 1
A;X;Y 1
```

then the call of 
```
call_tree.py -p ".*B.*" inputfile.txt
```

will output

```
A
 B
  C
   D
  E
```
so that the result will be the tree of all stacktraces that contained the "B" substring (matches by regular expression).



# ORIGINAL TEXT OF THE AUTHOR (ANDREW JOHNSON)

This directory contains utilities for visualizing the output of the profiler.  Some example flame graphs are in the `example_flame_graphs` directory.

## influxdb-dashboard

This is a simple dashboard for visualizing the metrics produced by the profiler using the InfluxDB backend.  See the README in this directory for more information.

## graphite_dump.py

This script will dump the output of the profiler from Graphite and format it in a manner suitable for use with [FlameGraph](https://github.com/brendangregg/FlameGraph).  You must specify the Graphite host, the prefix of the metrics, and the start and end date for the period to be visualized.

### Usage
graphite_dump.py takes the following options:

Option | Meaning
-------|--------
-o     | Hostname where Graphite is running
-s     | Beginning of the time range to dump in the HH:MM_yyyymmdd format
-e     | End of the time range to dump in the HH:MM_yyyymmdd format
-p     | Prefix for the metrics to dump


An example invocation would be
```
graphite_dump.py -o graphitehost -s 19:48_20141230 -e 19:50_20141230 -p statsd-jvm-profiler.cpu.trace
```
