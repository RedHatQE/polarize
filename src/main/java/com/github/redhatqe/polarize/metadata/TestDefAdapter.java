package com.github.redhatqe.polarize.metadata;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An adapter to make it easier to (de)serialize a Meta<TestDefinition>
 */
public class TestDefAdapter {
    public DefTypes.Project[] projectID;     // An array (actually set) of projects this definition applies to
    public String[] testCaseID;
    public String author = "CI User";
    public String title = "";
    public String description = "";    // Must have description but may come from @Test
    public String setup = "";
    public String teardown = "";

    public DefTypes.Importance importance = DefTypes.Importance.HIGH;
    public DefTypes.PosNeg posneg = DefTypes.PosNeg.POSITIVE;
    public DefTypes.Level level = DefTypes.Level.COMPONENT;
    public DefTypes.Automation automation = DefTypes.Automation.AUTOMATED;
    public String script = "";                // path or name of automation script/method
    TestTypeAdapter testtype = new TestTypeAdapter();
    TestStepAdapter[] teststeps = {};

    // Rarely used
    public String assignee = "";
    public String initialEstimate = "";
    public String tags = "";
    public String component = "";
    public String subcomponent = "";
    public String upstream = "";

    // These are not directly used by the importer
    public String xmlDesc = "";
    public boolean update = false;

    static public class ParamAdapter {
        public String name;
        public String scope = "local";

        public static ParamAdapter convert(Param p) {
            ParamAdapter adap = new ParamAdapter();
            adap.name = p.name();
            adap.scope = p.scope();
            return adap;
        }
    }

    static public class TestTypeAdapter {
        public DefTypes.TestTypes testtype = DefTypes.TestTypes.FUNCTIONAL;
        public DefTypes.Subtypes subtype1 = DefTypes.Subtypes.EMPTY;
        public DefTypes.Subtypes subtype2 = DefTypes.Subtypes.EMPTY;

        public static TestTypeAdapter convert(TestType tType) {
            TestTypeAdapter adap = new TestTypeAdapter();
            adap.testtype = tType.testtype();
            adap.subtype1 = tType.subtype1();
            adap.subtype2 = tType.subtype2();
            return adap;
        }
    }

    static public class TestStepAdapter {
        String expected = "";         // Optional: What the expected value should be from running this
        String description = "";      // Optional: Description of what the step does
        ParamAdapter[] params =  {};

        public static TestStepAdapter convert(TestStep step) {
            TestStepAdapter adap = new TestStepAdapter();
            adap.expected = step.expected();
            adap.description = step.description();
            Param[] params = step.params();
            List<ParamAdapter> adaps = Arrays.stream(params)
                    .map(ParamAdapter::convert)
                    .collect(Collectors.toList());
            adap.params = new ParamAdapter[adaps.size()];
            for(int i = 0; i < adap.params.length; i++) {
                adap.params[i] = adaps.get(i);
            }
            return adap;
        }
    }

    public static TestDefAdapter create(TestDefinition def) {
        TestDefAdapter adap = new TestDefAdapter();
        adap.assignee = def.assignee();
        //adap.author = def.author();
        adap.automation = def.automation();
        adap.component = def.component();
        adap.description = def.description();
        adap.importance = def.importance();
        adap.initialEstimate = def.initialEstimate();
        adap.level = def.level();
        adap.posneg = def.posneg();
        adap.projectID = def.projectID();
        adap.script = def.script();
        adap.setup = def.setup();
        adap.subcomponent = def.subcomponent();
        adap.tags = def.tags();
        adap.teardown = def.teardown();
        adap.testCaseID = def.testCaseID();
        List<TestStepAdapter> steps = Arrays.stream(def.teststeps())
                .map(TestStepAdapter::convert)
                .collect(Collectors.toList());
        adap.teststeps =  new TestStepAdapter[steps.size()];
        for(int i = 0; i < adap.teststeps.length; i++) {
            adap.teststeps[i] = steps.get(i);
        }
        adap.testCaseID = def.testCaseID();
        adap.testtype = TestTypeAdapter.convert(def.testtype());
        adap.update = def.update();
        adap.upstream = def.upstream();
        adap.xmlDesc = def.xmlDesc();
        return adap;
    }
}
