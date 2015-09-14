#!/usr/bin/env python
from optparse import OptionParser
from influxdb import InfluxDBClient
from blist import sorteddict
import sys
import re
import os
import shutil

class InfluxDBDump:
    def __init__(self, host, port, username, password, database, prefix, tag_mapping, filter_filename, out_dir, sort_order):
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.database = database
        self.prefix = prefix
        self.tag_mapping = tag_mapping
        self.sort_order = sort_order
        self.client = InfluxDBClient(self.host, self.port, self.username, self.password, self.database)
        self.mapped_tags = self._construct_tag_mapping(prefix, tag_mapping)
        # read the filter file and compose a set of strings which should be excluded when exported
        self.filter_exclude = set()
        self.out_dir = out_dir
        if filter_filename:
            with open(filter_filename) as f:
                for s in f:
                    self.filter_exclude.add(s.rstrip())

    def get_tag_values(self, tagname):
        result = []
        query = "SHOW TAG VALUES FROM \"cpu.stats.size\" WITH KEY = \"" + tagname +"\""
        items = self.client.query(query).raw['series'][0]['values']
        for item in items:
            result.append(item[0])
        return result

    def get_jvms(self):
        return self.get_tag_values("jvmName")

    def get_hosts(self):
        return self.get_tag_values("host")

    def output_to_file(self, out_filename, tags):
        print "=== Making file %s" % out_filename
        clauses = ["%s ='%s'" % (tag, value) for (tag, value) in tags.iteritems()]
        query = 'select value from /^cpu.trace.*/ where %s' % " and ".join(clauses)
        print "running query: %s" % query
        metrics = self.client.query(query)
        try:
            series = metrics.raw['series']
        except KeyError:
            print "got an empty recordset"
            return

        print "putting metrics into a sorted dictionary..."
        traces = sorteddict()
        for metric in series:
            if re.match(r'cpu\.trace\.\d+', metric['name']):
                continue
            name = self._format_metric_name(metric['name'], 'cpu.trace.')
            value = sum([v[1] for v in metric['values']])
            if name in traces:
                traces[name] = traces[name] + value
            else:
                traces[name] = value

        print "output this dictionary to the file..."
        with open(out_filename, "w") as f:
            for t in traces:
                found = False
                for filter_string in self.filter_exclude:
                    if filter_string in t:
                        found = True
                        break
                if not found:
                    v = traces[t]
                    if t != v: # this is Andrew's  cpu.trace.23 = 23  measures;  I don't know what are they for
                        f.write('%s %d\n' % (t, v))
        print "output finished."

    def run(self):
        # clean the output directory and re-create the directory structure
        if (os.path.exists(self.out_dir)):
            shutil.rmtree(self.out_dir)
        os.mkdir(out_dir)

        # first we will generate a file for every PID separately, so let us get the pids first
        non_gidit = re.compile(r'[^\d]+')
        non_filename = re.compile(r'[^\w\.-]+')

        # dump files for every jvm
        jvms = self.get_jvms()
        for jvm in jvms:
            try:
                pid, host = jvm.split("@", 1)
            except ValueError:
                pid = jvm
                host = "unknown"
            # run a query to find out the date and time when measurements were started
            tags = dict(self.mapped_tags)
            tags["jvmName"] = jvm
            clauses = ["%s ='%s'" % (tag, value) for (tag, value) in tags.iteritems()]
            query = 'select value from "cpu.stats.size" where %s limit 1' % " and ".join(clauses)
            print "======== %s ======== " % jvm
            print "running query: %s" % query
            date = self.client.query(query).raw['series'][0]['values'][0][0]
            filename = os.path.join(self.out_dir, "jvm_" + non_gidit.sub('_', str(date)) + "_" + non_filename.sub('_', str(host)) + \
                       "_" + non_filename.sub('_', str(pid))  + ".txt")
            self.output_to_file(filename, tags)
            print ""

        # dump files for every host
        hosts = self.get_hosts()
        for host in hosts:
            # run a query to find out the date and time when measurements were started
            tags = dict(self.mapped_tags)
            tags["host"] = host
            clauses = ["%s ='%s'" % (tag, value) for (tag, value) in tags.iteritems()]
            query = 'select value from "cpu.stats.size" where %s limit 1' % " and ".join(clauses)
            print "======== %s ======== " % host
            print "running query: %s" % query
            date = self.client.query(query).raw['series'][0]['values'][0][0]
            filename = os.path.join(self.out_dir, "host_" + non_gidit.sub('_', str(date)) + "_" + non_filename.sub('_', str(host)) + ".txt")
            self.output_to_file(filename, tags)
            print ""

        # dump everything
        hosts = self.get_hosts()
        for host in hosts:
            # run a query to find out the date and time when measurements were started
            tags = dict(self.mapped_tags)
            clauses = ["%s ='%s'" % (tag, value) for (tag, value) in tags.iteritems()]
            query = 'select value from "cpu.stats.size" where %s limit 1' % " and ".join(clauses)
            print "======== ALL ======== "
            print "running query: %s" % query
            date = self.client.query(query).raw['series'][0]['values'][0][0]
            filename = os.path.join(self.out_dir, "all_" + non_gidit.sub('_', str(date)) + ".txt")
            self.output_to_file(filename, tags)
            print ""


    def _format_metric_name(self, name, prefix):
        tokens = name.replace(prefix, '').split('.')
        reverse = reversed(tokens)
        # line_numbers = [':'.join(r.rsplit('-', 1)) for r in reverse]
        line_numbers = []
        for r in reverse:
            split_list = r.rsplit('-', 1)
            if split_list[0][-1:] == '-':
                split_list[0]  = split_list[0][:-1]
            split_list[1] = split_list[1].zfill(4)
            if self.sort_order == "0":
                s = ':'.join(split_list)
            elif self.sort_order == "1":
                s = split_list[1] + ':' + split_list[0]
            elif self.sort_order == "2":
                s = split_list[0]
            line_numbers.append(s)
        return ';'.join(line_numbers).replace('-', '.')

    def _construct_tag_mapping(self, prefix, tag_mapping):
        mapped_tags = {}
        if tag_mapping:
            tag_names = tag_mapping.split('.')
            prefix_components = prefix.split('.')
            if len(tag_names) != len(prefix_components):
                raise Exception('Invalid tag mapping %s' % tag_mapping)
            zipped = zip(tag_names, prefix_components)
            for entry in zipped:
                if entry[0] != 'SKIP':
                    mapped_tags[entry[0]] = entry[1]
        else:
            mapped_tags['prefix'] = prefix
        return mapped_tags


def get_arg_parser():
    parser = OptionParser()
    parser.add_option('-o', '--host', dest='host', help='Hostname of InfluxDB server', metavar='HOST')
    parser.add_option('-r', '--port', dest='port', help='Port for InfluxDB HTTP API (defaults to 8086)', metavar='PORT')
    parser.add_option('-u', '--username', dest='username', help='Username with which to connect to InfluxDB', metavar='USER')
    parser.add_option('-p', '--password', dest='password', help='Password with which to connect to InfluxDB', metavar='PASSWORD')
    parser.add_option('-d', '--database', dest='database', help='InfluxDB database which contains profiler data', metavar='DB')
    parser.add_option('-e', '--prefix', dest='prefix', help='Metric prefix', metavar='PREFIX')
    parser.add_option('-t', '--tag-mapping', dest='mapping', help='Tag mapping for metric prefix', metavar='MAPPING')
    parser.add_option('-f', '--filter', dest='filter', help='Filter for strings (list of strings which WON''T go into the output)', metavar='FILTER')
    parser.add_option('-x', '--outputdir', dest='outputdir', help='File Prefix', metavar='FILEPREFIX')
    parser.add_option('-s', '--sortorder', dest='sortorder', help='Sort Order: 0 (default) = by names, 1 = by linenumbers, 2 = skip linenumbers', metavar='FILEPREFIX')
    return parser

if __name__ == '__main__':
    parser = get_arg_parser()
    args, _ = parser.parse_args()
    if not(args.host and args.username and args.password and args.database and args.prefix):
        parser.print_help()
        sys.exit(255)
    port = args.port or 8086
    tag_mapping = args.mapping or None
    filter_filename = args.filter or None
    out_dir = args.outputdir or ""
    sort_order = args.sortorder or "0"
    dumper = InfluxDBDump(args.host, port, args.username, args.password, args.database, args.prefix, tag_mapping, filter_filename, out_dir, sort_order)
    dumper.run()