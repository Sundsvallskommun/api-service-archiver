//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.2 
// See <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2022.02.28 at 11:45:52 AM CET 
//


package vo;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;attribute name="Typ" use="required"&gt;
 *         &lt;simpleType&gt;
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *             &lt;enumeration value="Ersätter"/&gt;
 *             &lt;enumeration value="Är ersatt med"/&gt;
 *             &lt;enumeration value="Referens"/&gt;
 *             &lt;enumeration value="Refereras av"/&gt;
 *             &lt;enumeration value="Kräver"/&gt;
 *             &lt;enumeration value="Krävs av"/&gt;
 *             &lt;enumeration value="Innehåller"/&gt;
 *             &lt;enumeration value="Ingår i"/&gt;
 *             &lt;enumeration value="Annan format version"/&gt;
 *             &lt;enumeration value="Är annat format av"/&gt;
 *             &lt;enumeration value="Har version"/&gt;
 *             &lt;enumeration value="Är version av"/&gt;
 *             &lt;enumeration value="Maskad version"/&gt;
 *             &lt;enumeration value="Är maskad version av"/&gt;
 *             &lt;enumeration value="Egen relationsdefinition"/&gt;
 *           &lt;/restriction&gt;
 *         &lt;/simpleType&gt;
 *       &lt;/attribute&gt;
 *       &lt;attribute name="AnnanTyp" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "content"
})
@XmlRootElement(name = "HandlingRelation")
@Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
public class HandlingRelation {

    @XmlValue
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    protected String content;
    @XmlAttribute(name = "Typ", required = true)
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    protected String typ;
    @XmlAttribute(name = "AnnanTyp")
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    protected String annanTyp;

    /**
     * Gets the value of the content property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    public String getContent() {
        return content;
    }

    /**
     * Sets the value of the content property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    public void setContent(String value) {
        this.content = value;
    }

    /**
     * Gets the value of the typ property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    public String getTyp() {
        return typ;
    }

    /**
     * Sets the value of the typ property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    public void setTyp(String value) {
        this.typ = value;
    }

    /**
     * Gets the value of the annanTyp property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    public String getAnnanTyp() {
        return annanTyp;
    }

    /**
     * Sets the value of the annanTyp property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    public void setAnnanTyp(String value) {
        this.annanTyp = value;
    }

}
