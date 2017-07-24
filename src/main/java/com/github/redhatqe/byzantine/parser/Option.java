package com.github.redhatqe.byzantine.parser;

/**
 * Class to determine what to do given an option.  An option (here, opt) is a flag that is associated with a Setter
 * method.  This is how we know that given an opt, we know what method to call.
 *
 * @param <V>
 */
public class Option<V> {
    public String opt;  // for example --testrun-template-id
    public V value;
    public V defaultTo = null;
    public String description;
    public Setter<V> setter;

    public Option(String o, String desc) {
        this.opt = o;
        this.description = desc;
    }
}
