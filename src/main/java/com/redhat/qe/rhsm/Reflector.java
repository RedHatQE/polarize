package com.redhat.qe.rhsm;

import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.testng.annotations.Test;
import com.redhat.qe.rhsm.MetaData;

/**
 * Created by stoner on 3/9/16.
 */
public class Reflector {

    public HashMap<String, List<MetaData>> testsToClasses;
    public List<MetaData> methods;
    private Set<String> testTypes;

    public Reflector(){
        testsToClasses = new HashMap<>();
        testTypes = new HashSet<>(Arrays.asList("AcceptanceTests", "Tier1Tests", "Tier2Tests", "Tier3Tests"));
    }

    public interface GetGroups<T> {
        ArrayList<String> getGroups(T t);
    }

    public interface GetField<C, R> {
        R getField(C c);
    }

    /**
     *
     * @param args
     */
    public void reflect(String[] args) {
        //Reflector reflector = new Reflector();

        /*
          We have 4 categories of tests:
          - Acceptance
          - Tier1Tests
          - Tier2Tests
          - Tier3Tests

          So we will have a mapping of key = plan type, value = class name
         */

        for(String s: args) {
            Reflections refl = new Reflections(s);

            GetGroups<Test> getGroups = C -> new ArrayList<>(Arrays.asList(C.groups()));
            GetField<Class<?>, String> getName = Class::getName;

            Set<Class<?>> classes = refl.getTypesAnnotatedWith(Test.class);
            for (Class<?> c : classes) {
                this.getAnnotations(c);
            }
        }
        this.showMap();
    }

    public void showMap() {
        this.testsToClasses.entrySet()
                .stream()
                .forEach((es) -> System.out.println(es.getKey() + "=" + es.getValue()));
    }

    public <T> List<MetaData> getMetaData(Class<T> c) {
        Method[] methods = c.getMethods();
        List<Method> meths = new ArrayList<>(Arrays.asList(methods));
        List<MetaData> classMethods =
                meths.stream()
                        .filter(m -> m.getAnnotation(Test.class) != null)
                        .map(m -> {
                            Test ann = m.getAnnotation(Test.class);
                            String desc = ann.description();
                            String className = c.getName();
                            String methName = m.getName();
                            String provider = ann.dataProvider();
                            Boolean isProvider = !provider.isEmpty();
                            Boolean enabled = ann.enabled();
                            //return className + "." + m.getName();
                            return new MetaData(methName, className, desc, enabled, isProvider, provider);
                        })
                        .filter(e -> !e.className.isEmpty() && !e.methodName.isEmpty())
                        .collect(Collectors.toList());
        return classMethods;
    }

    public <T> void getAnnotations(Class<T> c) {
        List<MetaData> classMethods = this.getMetaData(c);
        if(this.methods == null) {
            this.methods = classMethods;
        }
        else
            this.methods.addAll(classMethods);

        // Get the groups from the Test annotation, store it in a set
        Annotation ann = c.getAnnotation(Test.class);
        if (ann == null) return;
        String[] groups = ((Test) ann).groups();
        Set<String> groupSet = new TreeSet<>(Arrays.asList(groups));

        // Get only the groups from testTypes
        groupSet = groupSet.stream()
                .filter(testTypes::contains)
                .collect(Collectors.toSet());

        for (String g : groupSet) {
            if (!this.testsToClasses.containsKey(g)) {
                this.testsToClasses.put(g, classMethods);
            } else {
                this.testsToClasses.get(g).addAll(classMethods);
            }
        }
    }
}
