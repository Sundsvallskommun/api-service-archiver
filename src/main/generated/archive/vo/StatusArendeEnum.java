//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.2 
// See <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2022.01.19 at 12:33:39 PM CET 
//


package vo;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StatusArendeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="StatusArendeEnum"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="Öppet"/&gt;
 *     &lt;enumeration value="Vilande"/&gt;
 *     &lt;enumeration value="Stängt"/&gt;
 *     &lt;enumeration value="Makulerat"/&gt;
 *     &lt;enumeration value="Ad Acta."/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "StatusArendeEnum")
@XmlEnum
@Generated(value = "com.sun.tools.xjc.Driver", date = "2022-01-19T12:33:39+01:00", comments = "JAXB RI v2.3.2")
public enum StatusArendeEnum {

    @XmlEnumValue("\u00d6ppet")
    ÖPPET("\u00d6ppet"),
    @XmlEnumValue("Vilande")
    VILANDE("Vilande"),
    @XmlEnumValue("St\u00e4ngt")
    STÄNGT("St\u00e4ngt"),
    @XmlEnumValue("Makulerat")
    MAKULERAT("Makulerat"),
    @XmlEnumValue("Ad Acta.")
    AD_ACTA("Ad Acta.");
    private final String value;

    StatusArendeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static StatusArendeEnum fromValue(String v) {
        for (StatusArendeEnum c: StatusArendeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}