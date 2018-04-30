package de.beanfactory.stax.xmlstream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import static java.util.Objects.nonNull;

public class XmlStreamGenerator {

    private final JAXBContext jaxb;

    public XmlStreamGenerator(JAXBContext xmlContext) {
        this.jaxb = xmlContext;
    }

    /**
     * This method takes a template as a blueprint and allows a filter to modify the outgoing XML.
     * <p>
     * Based on StAX, it will use JAXB to marshal the template. Then, the template will be re-read into events.
     * For each event, the function is called with plenty of parameters to allow for precise filtering.
     * </p>
     * <p>
     * The filter function can return true, to copy the current event, and false to not copy it.
     * The filter function can use the output event stream to add more events at will.
     * </p>
     * <p>
     *     the OutsputStream given as xmlOut will receive the event-generated XML.
     * </p>
     * @param template the Object to be used as blueprint. This is marshalled and then read back as a series of events.
     * @param xmlOut this is the output stream for XML processing
     * @param function this is the filter function, it is called for each event.
     * @param <T> the type of the template.
     * @throws IOException stream ops
     * @throws JAXBException jaxb serialisations
     * @throws XMLStreamException xml stax stream ops
     */
    public <T> void produceXml(T template, OutputStream xmlOut, StreamGeneratorFilter<T> function) throws IOException, JAXBException, XMLStreamException {
        XMLEventReader source = null;
        XMLEventWriter generator = null;

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            jaxb.createMarshaller().marshal(template, bos);
            bos.flush();

            // now, the template can be read back from the stream.
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            source = XMLInputFactory.newInstance().createXMLEventReader(bis);
            generator = XMLOutputFactory.newInstance().createXMLEventWriter(xmlOut);

            Stack<XMLEvent> elementStack = new Stack<>();

            Map<String, Object> stateMap = new HashMap<>();
            while (source.hasNext()) {
                XMLEvent event = source.nextEvent();
                // push elements on stack BEFORE entering the function
                if (event.isStartElement()) {
                    elementStack.push(event);
                }

                if (function.filter(event, elementStack, stateMap, generator)) {
                    generator.add(event);
                }

                // pop elements from stack AFTER function has been executed
                if (event.isEndElement()) {
                    elementStack.pop();
                }
            }
        } finally {
            if (nonNull(source)) source.close();
            if (nonNull(generator)) generator.close();
        }
    }

    @FunctionalInterface
    public interface StreamGeneratorFilter<T> {

        /**
         * This callback is used by the {@link XmlStreamGenerator}
         * @param event the event we just read from the blueprint
         * @param elementStack the element stack, to be able to navigate to a position to be changed.
         * @param stateMap the statemap can be used to keep information between calls. Used only by callback
         * @param generator the output stream (XMLEventStream)
         * @return true, if the event shall be written, false otherwise.
         * @throws XMLStreamException if something breaks here ...
         */
        boolean filter(XMLEvent event, Stack<XMLEvent> elementStack, Map<String, Object> stateMap, XMLEventWriter generator) throws XMLStreamException;
    }
}
