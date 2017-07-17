package com.github.redhatqe.byzantine.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;


/**
 * Simple helper class to get the head and tail (or first and rest if you prefer).  Sadly, java doesn't have pattern
 * matching, but basically if we've got an empty array, then the first and second elements of the Tuple are empty.
 * If the array has only one element, the first element of the Tuple has that value, and the second element will be
 * an Optional.empty value.  If the array has no elements, the first and second values of the Tuple are Optional.empty.
 * Otherwise, the first element of the Tuple is Optional.of(T args[0]), and the second is Optional.of(T[] args[1..n])
 */
public class ArgHelper {
    public static Logger logger = LogManager.getLogger("byzantine.ArgHelper");
    public static <T> Tuple<Optional<T>, Optional<T[]>> headAndTail(T[] args) {
        Tuple<Optional<T>, Optional<T[]>> res;
        if (args == null || args.length == 0) { // null array or empty array
            res = new Tuple<>(Optional.empty(), Optional.empty());
        }
        else if (args.length == 1) {
            res = new Tuple<>(Optional.of(args[0]), Optional.empty());
        }
        else {
            T[] newargs = Arrays.copyOfRange(args, 1, args.length);
            res = new Tuple<>(Optional.of(args[0]), Optional.of(newargs));
        }
        return res;
    }

    /**
     * If an argument which is a string contains non-allowable characters, replace them
     * @param in
     * @return
     */
    public static String sanitize(String in, Set<String> remove) {
        for (String check: remove) {
            if (in.contains(check))
                return in.replace(check, "");
        }
        return in;
    }

    public static String sanitize(String in) {
        Set<String> notAllowed = new HashSet<>();
        notAllowed.add("&quot");
        notAllowed.add("\"");
        return sanitize(in, notAllowed);
    }

    public static void main(String... args) {
        Tuple<Optional<String>, Optional<String[]>> ht = ArgHelper.headAndTail(args);
        String other = ht.first.orElse("hi");
        logger.info(other);

        Integer[] argsI = {10, 2, 3, 4, 5};
        Tuple<Optional<Integer>, Optional<Integer[]>> hti = ArgHelper.headAndTail(argsI);
        Integer i = hti.first.orElseThrow(() -> new Error("Should be 10"));
        hti.second.ifPresent(sec -> ArgHelper.headAndTail(sec).first.ifPresent(t -> logger.info(t)));
        logger.info(i);
    }
}
