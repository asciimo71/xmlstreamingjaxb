package de.beanfactory.stax.xmlstream.jaxb.model;

import com.migesok.jaxb.adapter.javatime.OffsetDateTimeXmlAdapter;
import lombok.Data;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"others", "aString", "aNumber", "aDateWithZone", "complexElement"})
public class ComplexXmlThing {
    @XmlElement(name="other")
    @XmlElementWrapper(name="others")
    List<String> others;

    @XmlElement(name="aString")
    String aString;

    @XmlElement(name="aNumber")
    Integer aNumber;

    @XmlElement(name="aDateWithZone")
    @XmlJavaTypeAdapter(OffsetDateTimeXmlAdapter.class)
    OffsetDateTime aDateWithZone;

    @XmlElement(name="sthcomplex")
    ComplexXmlThing complexElement;
}
