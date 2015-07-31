#!/usr/bin/env python
from optparse import OptionParser
import sys

parser = OptionParser()
parser.add_option('-f', '--filter', dest='filter', help='Filter for strings (list of strings which WON''T go into the output)', metavar='FILTER')
args, inputfile = parser.parse_args()
if len(inputfile) > 1:
    print "Format: filterlines.py [-f filter.txt] [inputfile.txt]"
    sys.exit(255)

# build the filtering set
filter_exclude = set()
if args.filter:
    with open(args.filter) as f:
        for s in f:
            s2 = s.rstrip()
            if s2 != "":
                filter_exclude.add(s2)

# read the file and output filtered lines
if len(inputfile) == 1:
    sys.stdin = open(inputfile[0], "r")

for line in sys.stdin.readlines():
    found = False
    for filter_string in filter_exclude:
        if filter_string in line:
            found = True
            break
    if not found:
        print(line.rstrip())
