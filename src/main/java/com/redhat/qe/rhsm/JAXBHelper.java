package com.redhat.qe.rhsm;

import javax.xml.bind.*;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Created by Sean Toner on 7/19/2016.
 */
public class JAXBHelper {
    /**
     * Generates
     * @param t An object whose class is annotated with @XmlRootElement
     * @param xmlpath
     * @param <T>
     */
    public static <T> void marshaller(T t, File xmlpath) {
        try {
            JAXBContext jaxbc = JAXBContext.newInstance(t.getClass());
            Marshaller marshaller = jaxbc.createMarshaller();
            marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(t, xmlpath);
            marshaller.marshal(t, System.out);
        } catch (JAXBException e) {
            e.printStackTrace();
        }

        // TODO: verify the function succeeded
    }


    /**
     * Generates an object of type T given an XML file
     *
     * @param t
     * @param xmlpath
     * @param <T>
     */
    public static <T> T unmarshaller(Class<T> t, File xmlpath) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        JAXBElement<T> ret;
        try {
            FileInputStream fis = new FileInputStream(xmlpath);
            XMLEventReader rdr = factory.createXMLEventReader(fis);
            JAXBContext jaxbc = JAXBContext.newInstance(t);

            Unmarshaller um = jaxbc.createUnmarshaller();
            ret = um.unmarshal(rdr, t);
            return ret.getValue();
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
        return null;
    }
}
