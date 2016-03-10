package com.redhat.qe.rhsm;

import org.reflections.Reflections;

import java.util.*;
import java.util.stream.Collectors;

import org.testng.annotations.Test;


/**
 * Created by stoner on 3/9/16.
 */
public class Reflector {

    public interface GetGroups<T> {
        ArrayList<String> getGroups(T t);
    }

    public interface GetField<C, R> {
        R getField(C c);
    }

    public static void main(String[] args) {
        ArrayList<Reflections> refls = new ArrayList<>();
        Set<Class<?>> tests = new HashSet<>();
        HashMap<String, String> groupToTestMethod = new HashMap<>();

        for(String s: args) {
            Reflections refl = new Reflections(s);

            GetGroups<Test> getGroups = C -> new ArrayList<>(Arrays.asList(C.groups()));
            GetField<Class<?>, String> getName = Class::getName;

            //Map<String, ArrayList<String>> groups =
            Set<Class<?>> classes = refl.getTypesAnnotatedWith(Test.class);
            //tests.addAll();
        }

        tests.forEach(System.out::println);
    }
}
