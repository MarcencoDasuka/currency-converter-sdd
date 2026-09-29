package com.converter.service;

import com.converter.entity.ExchangeRateEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class BnmXmlParser {

    private static final Logger log = LoggerFactory.getLogger(BnmXmlParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public record ParsedBulletin(
            LocalDate bulletinDate,
            String bulletinName,
            List<ExchangeRateEntity> rates
    ) {}

    public ParsedBulletin parse(String xmlContent, LocalDate fallbackDate) {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new IllegalArgumentException("XML content is empty or null");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE Protection
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (!"ValCurs".equalsIgnoreCase(root.getTagName())) {
                throw new IllegalArgumentException("Invalid XML root element: " + root.getTagName());
            }

            String dateStr = root.getAttribute("Date");
            LocalDate bulletinDate = fallbackDate;
            if (dateStr != null && !dateStr.isBlank()) {
                try {
                    bulletinDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
                } catch (Exception e) {
                    log.warn("Failed to parse Date attribute '{}' in ValCurs, using fallback {}", dateStr, fallbackDate);
                }
            }

            String bulletinName = root.getAttribute("name");
            NodeList valuteNodes = root.getElementsByTagName("Valute");
            List<ExchangeRateEntity> rates = new ArrayList<>();
            Instant now = Instant.now();

            for (int i = 0; i < valuteNodes.getLength(); i++) {
                Node node = valuteNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element valuteElem = (Element) node;
                    String externalId = valuteElem.getAttribute("ID");

                    String numCodeStr = getElementText(valuteElem, "NumCode");
                    String charCode = getElementText(valuteElem, "CharCode");
                    String nominalStr = getElementText(valuteElem, "Nominal");
                    String name = getElementText(valuteElem, "Name");
                    String valueStr = getElementText(valuteElem, "Value");

                    if (charCode == null || charCode.isBlank() || valueStr == null || valueStr.isBlank()) {
                        continue;
                    }

                    int numCode = 0;
                    try {
                        numCode = Integer.parseInt(numCodeStr.trim());
                    } catch (Exception ignored) {}

                    int nominal = 1;
                    try {
                        nominal = Integer.parseInt(nominalStr.trim());
                    } catch (Exception ignored) {}

                    // Handle comma as decimal separator if present
                    String normalizedValue = valueStr.trim().replace(',', '.');
                    BigDecimal rate = new BigDecimal(normalizedValue);

                    ExchangeRateEntity entity = new ExchangeRateEntity(
                            numCode,
                            charCode.trim().toUpperCase(),
                            name != null ? name.trim() : charCode.trim(),
                            nominal,
                            rate,
                            bulletinDate,
                            now,
                            externalId
                    );
                    rates.add(entity);
                }
            }

            return new ParsedBulletin(bulletinDate, bulletinName, rates);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse BNM XML: " + e.getMessage(), e);
        }
    }

    private String getElementText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0 && list.item(0) != null) {
            return list.item(0).getTextContent();
        }
        return null;
    }
}
