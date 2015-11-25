package com.movielabs.dcnc_cpl;

import java.util.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Facility {

    private static Map<String, String> facilities;
    private final static String fileName = "facilities.txt";

    static {
        facilities = new HashMap<String, String>();
	try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
                stream.forEach(s -> {
                        String[] v = s.split("\t");
                        facilities.put(v[0], v[1]);
                    });
                
            } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getLongName(String s) {
        return facilities.get(s);
    }
}
