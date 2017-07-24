package com.github.redhatqe.byzantine.configuration;

import com.github.redhatqe.byzantine.parser.Setter;

import java.io.IOException;
import java.util.Map;

/**
 * This interface is meant to be implemented by any class that represents the underlying data.  
 */
public interface IConfig {
    /**
     * This method will write the java object to a file
     * @param path
     */
    default public void writeConfig(String path) {
        if (path.endsWith(".yaml"))
            try {
                Serializer.toYaml(this, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        else if (path.endsWith(".json"))
            try {
                Serializer.toJson(this, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    /**
     * When a new class implementing IConfig is made, this method will setup a Map of String to Setter T and assign
     * it to a field of the object.  The map that is assigned here is looked up in the dispatch method.  So, given the
     * key passed to dispatch, it will look up in the Map that is initially assigned here and return the Setter object
     */
    void setupDefaultHandlers();

    /**
     * Allows a class to dynamically add or update a dispatch handler.  The implementing class will have a field of
     * type Map of String to Setter T This method will add or update to it.
     * @param name key in the map
     * @param setter value in the map
     * @param handlers map to add the name,setter pair
     */
    default <T> void addHandler(String name, Setter<T> setter, Map<String, Setter<T>> handlers) {
        handlers.put(name, setter);
    }

    // FIXME: I could not figure out a way to implement a getHandler in a class that implemented it.  For example, if I
    // had this:
    //Map<String, Setter<?>> getHandler(Class<?> cls);
    // There was no way to implement it.  This is not valid:
    // public Map<String, Setter<T>> getHandler(Class<t> cls) {
    //    if (String.class.isInstance(cls))
    //        return this.handlers;  // where this.handlers is Map<String, Setter<String>>
    // }
    Map<String, Setter<String>> sGetHandlers();
    Map<String, Setter<Boolean>> bGetHandlers();
    Map<String, Setter<Integer>> iGetHandlers();

    /**
     * This method will look at the specified key, and find a method from a Map
     * @param key
     * @return
     */
    default <T> Setter<T> dispatch(String key, Map<String, Setter<T>> handlers) {
        return handlers.get(key);
    }

    void setHelp(Boolean help);
}
