package com.dat3m.dartagnan.utils;

import static com.dat3m.dartagnan.utils.Result.FAIL;
import static com.dat3m.dartagnan.utils.Result.PASS;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class ResourceHelper {

    public static final String CAT_RESOURCE_PATH = "../";
    public static final String LITMUS_RESOURCE_PATH = "../";
    public static final String TEST_RESOURCE_PATH = "src/test/resources/";

    private static HashMap<String, Result> expectedResults;

    public static HashMap<String, Result> getExpectedResults() throws IOException {
        if(expectedResults == null){
            try (BufferedReader reader = new BufferedReader(new FileReader(TEST_RESOURCE_PATH + "dartagnan-expected.csv"))) {
                expectedResults = new HashMap<>();
                String str;
                while((str = reader.readLine()) != null){
                    String[] line = str.split(",");
                    if(line.length == 2){
                        expectedResults.put(line[0], Integer.parseInt(line[1]) == 1 ? FAIL : PASS);
                    }
                }
            }
        }
        return expectedResults;
    }
}
