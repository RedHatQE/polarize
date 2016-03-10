package com.redhat.qe.rhsm;

import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.testng.annotations.Test;


/**
 * Created by stoner on 3/9/16.
 */
public class Reflector {

    public HashMap<String, ArrayList<String>> testsToClasses;

    public Reflector(){
        testsToClasses = new HashMap<>();
    }

    public interface GetGroups<T> {
        ArrayList<String> getGroups(T t);
    }

    public interface GetField<C, R> {
        R getField(C c);
    }


    public static void main(String[] args) {
        Reflector reflector = new Reflector();

        /**
         * We have 4 categories of tests:
         * - Acceptance
         * - Tier1Tests
         * - Tier2Tests
         * - Tier3Tests
         *
         * So we will have a mapping of key = plan type, value = class name
         */

        Set<String> testTypes = new HashSet<>(Arrays.asList("AcceptanceTests", "Tier1Tests", "Tier2Tests",
                "Tier3Tests"));

        for(String s: args) {
            Reflections refl = new Reflections(s);

            GetGroups<Test> getGroups = C -> new ArrayList<>(Arrays.asList(C.groups()));
            GetField<Class<?>, String> getName = Class::getName;

            Set<Class<?>> classes = refl.getTypesAnnotatedWith(Test.class);
            for (Class<?> c : classes) {
                Method[] methods = c.getMethods();
                // Get the groups from the Test annotation, store it in a set
                Annotation ann = c.getAnnotation(Test.class);
                String[] groups = ((Test) ann).groups();
                Set<String> groupSet = new TreeSet<>(Arrays.asList(groups));

                // Get only the groups from testTypes
                groupSet = groupSet.stream()
                        .filter(testTypes::contains)
                        .collect(Collectors.toSet());

                // TODO: Figure out how to turn this into a stream using a reduce or collect to
                // store this to a Map
                // For each item in groupSet, add the group to the groupsToClass map as the key, and as the value
                // add an entry to the ArrayList
                for (String g : groupSet) {
                    if (!reflector.testsToClasses.containsKey(g)) {
                        ArrayList<String> classNames = new ArrayList<>();
                        classNames.add(c.getName());
                        reflector.testsToClasses.put(g, classNames);
                    } else {
                        reflector.testsToClasses.get(g).add(c.getName());
                    }
                }
            }
        }

        reflector.testsToClasses.entrySet()
                .stream()
                .forEach((es) -> System.out.println(es.getKey() + "=" + es.getValue()));
    }
}
