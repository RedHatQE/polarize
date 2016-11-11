package com.github.redhatqe.polarize.metadata;

public enum DefTypes {
    DUMMY;

    interface ToString {
        default String stringify() {
            return this.toString().toLowerCase();
        }
    }

    public enum Project {
        RHEL6, RedHatEnterpriseLinux7, PLATTP, My_Test_Project
    }

    public enum TestTypes implements ToString {
        FUNCTIONAL, NONFUNCTIONAL, STRUCTURAL;
    }

    public enum Action implements ToString {
        CREATE, UPDATE
    }

    public enum Level implements ToString {
        COMPONENT, INTEGRATION, SYSTEM, ACCEPTANCE
    }

    public enum PosNeg implements ToString {
        POSITIVE, NEGATIVE
    }

    public enum Importance implements ToString {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    public enum Automation implements ToString {
        AUTOMATED, NOTAUTOMATED, MANUALONLY
    }

    public enum Subtypes {
        EMPTY,
        COMPLIANCE,
        DOCUMENTATION,
        I18NL10N,
        INSTALLABILITY,
        INTEROPERABILITY,
        PERFORMANCE,
        RELIABILITY,
        SCALABILITY,
        SECURITY,
        USABILITY,
        RECOVERYFAILOVER;

        @Override
        public String toString() {
            String thisName = super.toString();
            if (thisName.equals("EMPTY"))
                return "-";
            return thisName.toLowerCase();
        }
    }

    /**
     * These are the custom fields for the TestCase WorkItem type (you will see these in the Polarion GUI)
     */
    public enum Custom implements ToString {
        CASEAUTOMATION,
        CASEIMPORTANCE,
        CASELEVEL,
        CASEPOSNEG,
        UPSTREAM,
        TAGS,
        SETUP,
        TEARDOWN,
        COMPONENT,
        SUBCOMPONENT,
        AUTOMATION_SCRIPT,
        TESTTYPE,
        SUBTYPE1,
        SUBTYPE2
    }

    public enum Severity implements ToString {
        MUSTHAVE, SHOULDHAVE, NICETOHAVE, WILLNOTHAVE
    }

    /**
     * Taken from Polarion GUI on a WorkItem under Linked Work Items and if you select "role" drop down
     */
    public enum Role implements  ToString {
        RELATES_TO,
        HAS_PARENT,
        DUPLICATES,
        VERIFIES,
        IS_RELATED_TO,
        IS_PARENT_OF,
        IS_DUPLICATED_BY,
        TRIGGERS
    }
}
