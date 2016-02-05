/*
 * Copyright (c) 2015 MovieLabs
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
