#!/usr/bin/env python
from optparse import OptionParser
from blist import sorteddict
import sys
import re

class Trie:
    def __init__(self):
        self.methods = sorteddict()

    def push(self, method_list):
        if len(method_list) == 0:
            return
        m, rest = method_list[0], method_list[1:]
        if not m in self.methods:
            self.methods[m] = Trie()
        self.methods[m].push(rest)

    def __dump__(self, f, n, tabs):
        for m in self.methods:
            f.write(" " * n * tabs + m + "\n")
            self.methods[m].__dump__(f, n + 1, tabs)

    def dump(self, f, tabs):
        self.__dump__(f, 0, tabs)

def get_arg_parser():
    parser = OptionParser()
    parser.add_option('-p', '--pattern', dest='pattern', help='Regex pattern to filter the input lines', metavar='PATTERN')
    return parser

if __name__ == '__main__':
    parser = get_arg_parser()
    args, inputfile = parser.parse_args()
    if len(inputfile) > 1:
        print "Format: make_tree.py [-p \"regex\"] [inputfile.txt]"
        sys.exit(255)
    if len(inputfile) == 1:
        sys.stdin = open(inputfile[0], "r")
    pattern = args.pattern or ".*"
    trie = Trie()
    for line in sys.stdin:
        line = line.strip().split(" ", 1)[0]
        if re.match(pattern, line):
            trie.push(line.split(";"))
    trie.dump(sys.stdout, 1)