//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.2 
// See <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2022.02.28 at 11:45:52 AM CET 
//


package vo;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element ref="{http://xml.ra.se/e-arkiv/FGS-ERMS}Egenskap" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "egenskap"
})
@XmlRootElement(name = "Egenskaper")
@Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
public class Egenskaper {

    @XmlElement(name = "Egenskap", required = true)
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    protected List<Egenskap> egenskap;

    /**
     * Gets the value of the egenskap property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the egenskap property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEgenskap().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Egenskap }
     * 
     * 
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2022-02-28T11:45:52+01:00", comments = "JAXB RI v2.3.2")
    public List<Egenskap> getEgenskap() {
        if (egenskap == null) {
            egenskap = new ArrayList<Egenskap>();
        }
        return this.egenskap;
    }

}
