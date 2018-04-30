package de.beanfactory.stax.xmlstream.jaxb;

import de.beanfactory.stax.xmlstream.XmlStreamGenerator;
import de.beanfactory.stax.xmlstream.jaxb.model.ComplexXmlBean;
import de.beanfactory.stax.xmlstream.jaxb.model.ComplexXmlThing;
import org.junit.Test;

import javax.xml.bind.*;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class XmlStreamWithJaxBApplicationTests {

    @Test
    public void contextLoads() {
    }


    @Test
    public void itWillWorkWithJaxb() throws Exception {
        JAXBContext ctx = JAXBContext.newInstance(ComplexXmlBean.class);
        Marshaller m = ctx.createMarshaller();
        Unmarshaller u = ctx.createUnmarshaller();

        ComplexXmlBean bean = createObject();

        m.marshal(bean, new FileOutputStream("out.xml"));
        ComplexXmlBean result = (ComplexXmlBean) u.unmarshal(new File("out.xml"));

        assertEquals(bean, result);
    }

    @Test
    public void itShouldCopyEventsToTheStreamOnFilterTrue() throws Exception {
        final JAXBContext ctx = JAXBContext.newInstance(ComplexXmlBean.class);
        XmlStreamGenerator generator = new XmlStreamGenerator(ctx);

        ComplexXmlBean bean = createObject();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        generator.produceXml(bean, bos, (event, stack, stateMap, generator1) -> true);
        bos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        JAXBElement<ComplexXmlBean> result = ctx.createUnmarshaller().unmarshal(new StreamSource(bis), ComplexXmlBean.class);
        assertEquals(bean, result.getValue());
    }

    @Test
    public void itShouldSuppressAllOthersEventsToTheStream() throws Exception {
        final JAXBContext ctx = JAXBContext.newInstance(ComplexXmlBean.class);
        XmlStreamGenerator generator = new XmlStreamGenerator(ctx);

        ComplexXmlBean bean = createObject();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        generator.produceXml(bean, bos, (event, stack, stateMap, generator1) -> {
            if (event.isStartElement() && event.asStartElement().getName().getLocalPart().equals("others") &&
                    !stateMap.containsKey("s")) {
                stateMap.put("s", "");
                return false;
            } else if (event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("others") &&
                    stateMap.containsKey("s")) {
                stateMap.remove("s");
                return false;
            } else if (stateMap.containsKey("s")) { // skip in between...
                return false;
            }

            return true;
        });
        bos.close();

        Files.write(Paths.get("out2.xml"), bos.toByteArray(), StandardOpenOption.CREATE);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        JAXBElement<ComplexXmlBean> result = ctx.createUnmarshaller().unmarshal(new StreamSource(bis), ComplexXmlBean.class);
        assertNull(result.getValue().getThings().get(0).getOthers());
    }

    @Test
    public void itShouldSuppressFirstOthersEventsToTheStream() throws Exception {
        final JAXBContext ctx = JAXBContext.newInstance(ComplexXmlBean.class);
        XmlStreamGenerator generator = new XmlStreamGenerator(ctx);

        ComplexXmlBean bean = createObject();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        generator.produceXml(bean, bos, (event, stack, stateMap, generator1) -> {
            if (event.isStartElement() && event.asStartElement().getName().getLocalPart().equals("others") && stack.size() <= 4) {
                if (stateMap.containsKey("s")) {
                    return stack.size() < (Integer) stateMap.get("s");
                }
                stateMap.put("s", stack.size());
                return false;
            } else if (event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("others") &&
                    stateMap.containsKey("s") &&
                    stack.size() <= 4) {
                stateMap.remove("s");
                return false;
            } else if (stateMap.containsKey("s")) { // skip in between...
                return false;
            }

            return true;
        });
        bos.flush();
        bos.close();

        Files.write(Paths.get("out3.xml"), bos.toByteArray(), StandardOpenOption.CREATE);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        JAXBElement<ComplexXmlBean> result = ctx.createUnmarshaller().unmarshal(new StreamSource(bis), ComplexXmlBean.class);
        assertNull(result.getValue().getThings().get(0).getOthers());
    }

    @Test
    public void itShouldGenerateAStreamOfEventsToTheStream() throws Exception {
        final JAXBContext ctx = JAXBContext.newInstance(ComplexXmlBean.class);
        XmlStreamGenerator generator = new XmlStreamGenerator(ctx);

        ComplexXmlBean bean = createObject();
        bean.getThings().get(0).getOthers().clear(); // no more others in the first Thing
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        final XMLEventFactory evFac = XMLEventFactory.newInstance();
        generator.produceXml(bean, bos, (event, stack, stateMap, generator1) -> {
            if (event.isStartElement()
                    && event.asStartElement().getName().getLocalPart().equals("others")
                    && stack.size() <= 4
                    && !stateMap.containsKey("first")) {
                stateMap.put("first", stack.size());
                generator1.add(event);
                Arrays.stream("This is a new set äöüß <>& of others".split(" ")).forEach(s -> {
                    try {
                        generator1.add(evFac.createStartElement(QName.valueOf("other"), null, null));
                        generator1.add(evFac.createCData(s));
                        generator1.add(evFac.createEndElement(QName.valueOf("other"), null));
                    } catch (XMLStreamException e) {
                        e.printStackTrace();
                        fail();
                    }
                });
                return false;
            }

            return true;
        });
        bos.flush();
        bos.close();

        Files.write(Paths.get("out4.xml"), bos.toByteArray(), StandardOpenOption.CREATE);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        JAXBElement<ComplexXmlBean> result = ctx.createUnmarshaller().unmarshal(new StreamSource(bis), ComplexXmlBean.class);
        assertNotNull(result.getValue().getThings().get(0).getOthers());
        assertEquals(9, result.getValue().getThings().get(0).getOthers().size());
        assertEquals("This", result.getValue().getThings().get(0).getOthers().get(0));
    }


    @Test
    public void itShouldGenerateJaxbObjectsAsEventsToTheStream() throws Exception {
        final JAXBContext ctx = JAXBContext.newInstance(ComplexXmlBean.class);
        final Marshaller m = ctx.createMarshaller();
        m.setProperty(Marshaller.JAXB_FRAGMENT, true);

        XmlStreamGenerator generator = new XmlStreamGenerator(ctx);

        ComplexXmlBean bean = createObject();
        bean.getThings().clear(); // no more things
        bean.setComplexElement(null); // no inner object

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        generator.produceXml(bean, bos, (event, stack, stateMap, generator1) -> {
            if (event.isStartElement()
                    && event.asStartElement().getName().getLocalPart().equals("things")) {
                // add the event NOW
                generator1.add(event);

                Arrays.stream("This is a new set äöüß <>& of others".split(" ")).forEach(s -> {
                    ComplexXmlThing thing = new ComplexXmlThing();
                    thing.setAString(s);
                    try {
                        JAXBElement<ComplexXmlThing> je = new JAXBElement<>(QName.valueOf("thing"), ComplexXmlThing.class, thing);
                        m.marshal(je, generator1);
                    } catch (JAXBException e) {
                        e.printStackTrace();
                        fail();
                    }
                });

                // do not add the <things> event again!
                return false;
            }

            // passthrough all other events
            return true;
        });

        bos.flush();
        bos.close();

        Files.write(Paths.get("out5.xml"), bos.toByteArray(), StandardOpenOption.CREATE);

        // Reread the event
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        JAXBElement<ComplexXmlBean> result = ctx.createUnmarshaller().unmarshal(new StreamSource(bis), ComplexXmlBean.class);

        assertNull(result.getValue().getThings().get(0).getOthers());
        assertEquals("This", result.getValue().getThings().get(0).getAString());
        assertEquals(9, result.getValue().getThings().size());
    }


    // some generator objects
    private ComplexXmlBean createObject() {
        ComplexXmlBean x = new ComplexXmlBean();
        OffsetDateTime now;
        x.setDate((now = OffsetDateTime.now()).format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
        x.setName("attribute Name");
        x.setNumber(4711);
        x.setADateWithZone(now);
        x.setANumber(4712);
        x.setAString("Element on String");
        x.setComplexElement(createThing(3));
        x.setOthers(Arrays.stream(new String[10]).map(s -> String.valueOf(Math.round(Math.random() * 20))).collect(Collectors.toList()));
        x.setThings(Arrays.stream(new ComplexXmlThing[10]).map(t -> this.createThing(3)).collect(Collectors.toList()));
        return x;
    }

    private ComplexXmlThing createThing(final int createInner) {
        ComplexXmlThing x = new ComplexXmlThing();

        x.setADateWithZone(OffsetDateTime.now());
        x.setANumber(Long.valueOf(Math.round(Integer.MAX_VALUE * Math.random())).intValue());
        x.setAString("Internal String");
        x.setOthers(Arrays.stream(new String[10]).map(s -> String.valueOf(createInner * 20 + Math.round(Math.random() * 20))).collect(Collectors.toList()));

        if (createInner > 0) x.setComplexElement(createThing(createInner - 1));

        return x;
    }
}
