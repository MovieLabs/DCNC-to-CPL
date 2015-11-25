package com.movielabs.dcnc_cpl;

import java.io.*;
import java.util.*;
import java.text.ParseException;

import org.apache.commons.cli.*;

public class DCNCtoCPL {
    private static boolean verbose = false;


    private enum Opts {
        s, o, v
    }

    public static void usage() {
        System.out.println("DCNCtoCPL [-v] -s DCNC-string [-o outputfile]");
    }

    public static void main(String[] args) throws Exception {
        String dcnc;
        String outFile;
            
        Options options = new Options();
        options.addOption(Opts.s.name(), true, "specify DCNC string");
        options.addOption(Opts.o.name(), true, "specify output file name");
        options.addOption(Opts.v.name(), false, "verbose");
            
        CommandLineParser cli = new DefaultParser();

        try {
            CommandLine cmd = cli.parse(options, args);

            dcnc = cmd.getOptionValue(Opts.s.name());
            if (dcnc == null)
                throw new ParseException("input file not specified", 0);

            outFile = cmd.getOptionValue(Opts.o.name());
            if (outFile == null)
                throw new ParseException("output file not specified", 0);

            verbose = cmd.hasOption(Opts.v.name());

            DigitalCinemaName dcn = new DigitalCinemaName(verbose);

            dcn.parse(dcnc);

            dcn.makeXML(outFile);
        } catch (ParseException e) {
            System.out.println( "bad command line: " + e.getMessage() );
            usage();
            System.exit(-1);
        }

    }
}
