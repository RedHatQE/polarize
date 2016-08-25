package com.github.redhatqe.polarize.metadata;

public enum DefTypes {
    DUMMY;

    interface ToString {
        default String stringify() {
            return this.toString().toLowerCase();
        }
    }

    public enum Project {
        RHEL6, RedHatEnterpriseLinux7, PLATTP
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
}
