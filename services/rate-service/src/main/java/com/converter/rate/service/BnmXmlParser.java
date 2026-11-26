package com.converter.rate.service;

import com.converter.rate.entity.ExchangeRateEntity;
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
import java.util.Locale;

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
                } catch (Exception ex) {
                    log.warn("Failed to parse Date attribute '{}', using fallback {}", dateStr, fallbackDate);
                }
            }

            String bulletinName = root.getAttribute("name");
            NodeList valuteNodes = root.getElementsByTagName("Valute");
            List<ExchangeRateEntity> rates = new ArrayList<>();
            Instant now = Instant.now();

            for (int i = 0; i < valuteNodes.getLength(); i++) {
                Node node = valuteNodes.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
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

                int numCode = parseIntOrDefault(numCodeStr, 0);
                int nominal = Math.max(1, parseIntOrDefault(nominalStr, 1));
                BigDecimal rate = new BigDecimal(valueStr.trim().replace(',', '.'));

                rates.add(new ExchangeRateEntity(
                        numCode,
                        charCode.trim().toUpperCase(Locale.ROOT),
                        name != null && !name.isBlank() ? name.trim() : charCode.trim().toUpperCase(Locale.ROOT),
                        nominal,
                        rate,
                        bulletinDate,
                        now,
                        externalId
                ));
            }

            return new ParsedBulletin(bulletinDate, bulletinName, rates);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse BNM XML: " + ex.getMessage(), ex);
        }
    }

    private static int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String getElementText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0 && list.item(0) != null) {
            return list.item(0).getTextContent();
        }
        return null;
    }
}
