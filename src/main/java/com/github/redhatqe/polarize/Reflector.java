package com.github.redhatqe.polarize;

import com.github.redhatqe.polarize.metadata.DefTypes;
import com.github.redhatqe.polarize.metadata.Meta;
import com.github.redhatqe.polarize.metadata.TestDefAdapter;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/**
 * Created by stoner on 3/9/16.
 */
public class Reflector {

    public HashMap<String, List<MetaData>> testsToClasses;
    public List<MetaData> methods;
    public List<Meta<TestDefinition>> testDefs;
    public List<Meta<TestDefAdapter>> testDefAdapters;
    public Map<String, Map<String, Meta<TestDefinition>>> methodToTestDefs;
    private Set<String> testTypes;
    private Logger logger = LoggerFactory.getLogger(Reflector.class);

    public Reflector(){
        testsToClasses = new HashMap<>();
        testTypes = new HashSet<>(Arrays.asList("AcceptanceTests", "Tier1Tests", "Tier2Tests", "Tier3Tests"));
        methodToTestDefs = new HashMap<>();
        testDefs = new ArrayList<>();
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

    private void showMap() {
        this.testsToClasses.entrySet().forEach((es) -> System.out.println(es.getKey() + "=" + es.getValue()));
    }

    private <T> List<Meta<TestDefinition>> getTestDefMetaData(Class<T> c) {
        Method[] methods = c.getMethods();
        List<Method> meths = new ArrayList<>(Arrays.asList(methods));
        List<Method> filtered = meths.stream()
                        .filter(m -> m.getAnnotation(TestDefinition.class) != null)
                        .collect(Collectors.toList());
        return filtered.stream().flatMap(m -> {
                            TestDefinition ann = m.getAnnotation(TestDefinition.class);
                            String className = c.getName();
                            Package pkg = c.getPackage();
                            String p = pkg.getName();
                            String methName = m.getName();
                            String qual = className + "." + methName;
                            DefTypes.Project[] projects = ann.projectID();
                            String[] polarionIDs = ann.testCaseID();
                            if (polarionIDs.length > 0 && polarionIDs.length != projects.length)
                                this.logger.error("Length of projects and polarionIds not the same");

                            List<Meta<TestDefinition>> metas = new ArrayList<>();
                            for(int i = 0; i < projects.length; i++) {
                                String project = projects[i].toString();
                                String id;
                                if (polarionIDs.length == 0)
                                    id = "";
                                else
                                    id = polarionIDs[i];
                                metas.add(Meta.create(qual, methName, className, p, project, id, ann));
                            }
                            return metas.stream().map( me -> me);
                        })
                        .filter(meta -> !meta.className.isEmpty() && !meta.methName.isEmpty())
                        .collect(Collectors.toList());
    }

    /**
     * Gets any methods annotated with TestDefinition
     *
     * @param c
     * @param <T>
     * @return
     */
    public <T> List<MetaData> getTestNGMetaData(Class<T> c) {
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
        List<MetaData> classMethods = this.getTestNGMetaData(c);
        if(this.methods == null) {
            this.methods = classMethods;
        }
        else
            this.methods.addAll(classMethods);

        this.testDefs.addAll(this.getTestDefMetaData(c));

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
