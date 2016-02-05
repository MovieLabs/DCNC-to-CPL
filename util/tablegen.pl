#!/usr/bin/perl
#
# Copyright (c) 2015 MovieLabs
# 
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files (the
# "Software"), to deal in the Software without restriction, including
# without limitation the rights to use, copy, modify, merge, publish,
# distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to
# the following conditions:
# 
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
# LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
# OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
# WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

open(FILE, "<Studio-template.txt");
open(OFILE, ">Studio.java");
while(<FILE>) {
    if (/^\%DeAdBeEf\%$/) {
        open(SFILE, "<studios.txt");
        while(<SFILE>) {
            chomp;
            my @f = split("\t", $_);
            printf OFILE "%sstudios.put(\"%s\", \"%s\");\n", " " x 8, $f[0], $f[1];
        }
        close(SFILE);
    } else {
        print OFILE;
    }
}
close(FILE);
close(OFILE);

open(FILE, "<Facility-template.txt");
open(OFILE, ">Facility.java");
while(<FILE>) {
    if (/^\%DeAdBeEf\%$/) {
        open(SFILE, "<facilities.txt");
        while(<SFILE>) {
            chomp;
            my @f = split("\t", $_);
            printf OFILE "%sfacilities.put(\"%s\", \"%s\");\n", " " x 8, $f[0], $f[1];
        }
        close(SFILE);
    } else {
        print OFILE;
    }
}
close(FILE);
close(OFILE);
