package com.github.redhatqe.byzantine.configurator;

import com.github.redhatqe.byzantine.configuration.IConfig;
import com.github.redhatqe.byzantine.utils.Tuple;
import joptsimple.OptionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface ICLIConfig {


    static String[] tupleListToArray(List<Tuple<String, String>> args) {
        List<String> argList = args.stream()
                .flatMap(t -> {
                    List<String> list = new ArrayList<>();
                    list.add(t.first);
                    list.add(t.second);
                    return list.stream();
                })
                .filter(s -> !s.equals(""))
                .collect(Collectors.toList());
        return argList.toArray(new String[0]);
    }

    static List<Tuple<String, String>> arrayToTupleList(String[] args) {
        List<Tuple<String, String>> argList = new ArrayList<>();
        Tuple<String, String> tuple;
        int i = 0;
        while(i < args.length) {
            try {
                tuple = new Tuple<>(args[i], args[i + 1]);
            }
            catch (IndexOutOfBoundsException ex) {
                tuple = new Tuple<>(args[i], "");
            }
            argList.add(tuple);
            i += 2;
        }

        return argList;
    }

    /**
     * This is where all the cli options this data type accepts are given.  The cfg object we pass in will call its
     * dispatch method given the name of some option which will look up in one of its handler maps and return the
     * method used to set the value.  That way, when the parser is called,
     */
    public <T1 extends IConfig> void setupNameToHandler(T1 cfg);

    Boolean printHelp(OptionSet opts);

    //void writeConfig(OptionSet opts);

    void parse(IConfig cfg, String... args);
}
