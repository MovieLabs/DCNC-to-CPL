#!/usr/bin/perl

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
