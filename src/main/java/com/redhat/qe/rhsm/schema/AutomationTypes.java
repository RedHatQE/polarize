//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.06.30 at 02:05:36 PM EDT 
//


package com.redhat.qe.rhsm.schema;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for automation-types.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="automation-types">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="automated"/>
 *     &lt;enumeration value="manual_only"/>
 *     &lt;enumeration value="not_automated"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "automation-types")
@XmlEnum
public enum AutomationTypes {

    @XmlEnumValue("automated")
    AUTOMATED("automated"),
    @XmlEnumValue("manual_only")
    MANUAL_ONLY("manual_only"),
    @XmlEnumValue("not_automated")
    NOT_AUTOMATED("not_automated");
    private final String value;

    AutomationTypes(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AutomationTypes fromValue(String v) {
        for (AutomationTypes c: AutomationTypes.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
