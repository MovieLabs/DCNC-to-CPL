package com.movielabs.dcnc_cpl;

import java.util.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Studio {

    private static Map<String, String> studios;
    private final static String fileName = "studios.txt";

    static {
        studios = new HashMap<String, String>();
	try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
                stream.forEach(s -> {
                        String[] v = s.split("\t");
                        studios.put(v[0], v[1]);
                    });
                
            } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getLongName(String s) {
        return studios.get(s);
    }
}
