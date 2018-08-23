/*
 * Copyright 2007-2018 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */

package eu.europeana.fulltext.loader.service;

import eu.europeana.fulltext.loader.exception.MissingDataException;
import eu.europeana.fulltext.loader.model.AnnoPageRdf;
import eu.europeana.fulltext.loader.model.AnnotationRdf;
import eu.europeana.fulltext.loader.model.TargetRdf;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by luthien on 18/07/2018.
 */

public class XMLXPathParser {

    private static final String CHARPOS    = "#char=";
    private static final String XYWHPOS    = "#xywh=";
    private static final String PAGEDCTYPE = "Page";

    public static AnnoPageRdf eatIt(Path path) {
        return eatIt(path.toString(), readFileContents(path), StringUtils.split(path.getFileName().toString(), '.')[0]);
    }

    /**
     *
     * @param path fileName, for logging purposes only so we know which file contains errors
     * @param xmlString
     * @param pageId
     * @return
     */
    public static AnnoPageRdf eatIt(String path, String xmlString, String pageId) {
        String entityText = null;
        AnnoPageRdf ap = null;

        try {
            DocumentBuilderFactory factory  = DocumentBuilderFactory.newInstance();
            DocumentBuilder        builder  = factory.newDocumentBuilder();
            Document               document = builder.parse(new InputSource(new StringReader(xmlString)));

            String internalSubset = (document).getDoctype().getInternalSubset();

            String imgTargetBase = readEntityString("img", internalSubset);
            entityText = readEntityString("text", internalSubset);

            XPathFactory xPathfactory = XPathFactory.newInstance();
            factory.setNamespaceAware(true);
            XPath ftXpath = xPathfactory.newXPath();

            XPathExpression ftExpr   = ftXpath.compile("//*[local-name()='FullTextResource']");
            Object          ftResult = ftExpr.evaluate(document, XPathConstants.NODESET);
            NodeList        ftNodes  = (NodeList) ftResult;

            // note that getTextContent will automatically convert escaped xml characters to their proper value
            String ftResource = ftNodes.item(0).getAttributes().item(0).getTextContent();
            if (ftResource == null) {
                // TODO check with Hugo if there is any point in saving annotations if we don't have a resource
                throw new MissingDataException("No resource found!");
            }

            if (!ftNodes.item(0).getChildNodes().item(1).getNodeName().equalsIgnoreCase("dc:language")) {
                // TODO check with Hugo if we can assume that language and text is always present and always in the same order
                throw new MissingDataException("No resource dc:language definition found!");
            }
            String ftLang     = ftNodes.item(0).getChildNodes().item(1).getTextContent();

            if (!ftNodes.item(0).getChildNodes().item(3).getNodeName().equalsIgnoreCase("rdf:value")) {
                throw new MissingDataException("No resource rdf:value definition found!");
            }
            String ftText     = ftNodes.item(0).getChildNodes().item(3).getTextContent();


            // TODO break this up into submethods

            XPath           anXpath  = xPathfactory.newXPath();
            XPathExpression anExpr   = anXpath.compile("//*[local-name()='Annotation']");
            Object          anResult = anExpr.evaluate(document, XPathConstants.NODESET);
            NodeList        aNodes   = (NodeList) anResult;

            List<AnnotationRdf> annoList          = new ArrayList<>();
            AnnotationRdf pageAnnotationRdf = null;

            for (int i = 0; i < aNodes.getLength(); i++) {
                boolean         annotationError  = false;
                boolean         isPageAnnotation = false;
                Node            aNode            = aNodes.item(i);
                String          id               = "";
                String          dcType           = "";
                String          motiv            = "";
                String          specRes          = "";
                String          resource         = "";
                String          resLang          = "";
                List<TargetRdf> targetRdfList    = new ArrayList<>();

                id = StringUtils.removeStart(aNode.getAttributes().item(0).getTextContent(), "/");

                if (aNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element anElement = (Element) aNode;
                    dcType = anElement.getElementsByTagName("dc:type").item(0).getTextContent();
                    motiv = anElement.getElementsByTagName("oa:motivatedBy")
                                     .item(0)
                                     .getAttributes()
                                     .item(0)
                                     .getNodeValue();

                    Node bodyNode = anElement.getElementsByTagName("oa:hasBody").item(0);

                    if (StringUtils.equalsIgnoreCase(dcType, PAGEDCTYPE)) {
                        isPageAnnotation = true;
                        resource = anElement.getElementsByTagName("oa:hasBody")
                                            .item(0)
                                            .getAttributes()
                                            .item(0)
                                            .getNodeValue();
                    } else if (bodyNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element bodyElement = (Element) bodyNode;
                        if (bodyElement.getElementsByTagName("oa:SpecificResource").getLength() == 0){
                            resource = anElement.getElementsByTagName("oa:hasBody")
                                                .item(0)
                                                .getAttributes()
                                                .item(0)
                                                .getNodeValue();
                            specRes = resource; // in case of 'bodyless' Annotations, make these two equal

                        } else {
                            specRes = bodyElement.getElementsByTagName("oa:SpecificResource")
                                                 .item(0)
                                                 .getAttributes()
                                                 .item(0)
                                                 .getNodeValue();

                            Node specResNode = bodyElement.getElementsByTagName("oa:SpecificResource").item(0);
                            if (specResNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element specResElement = (Element) specResNode;
                                resource = specResElement.getElementsByTagName("oa:hasSource")
                                                         .item(0)
                                                         .getAttributes()
                                                         .item(0)
                                                         .getNodeValue();
                                if (specResElement.getElementsByTagName("dc:language").getLength() > 0){
                                    resLang = specResElement.getElementsByTagName("dc:language")
                                                             .item(0)
                                                             .getTextContent();
                                }
                            }
                        }
                    }

                    NodeList targetNodes = anElement.getElementsByTagName("oa:hasTarget");
                    for (int j = 0; j < targetNodes.getLength(); j++) {
                        Node targetNode = targetNodes.item(j);
                        if (targetNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element targetElement = (Element) targetNode;
                            if (!isPageAnnotation) {
                                try {
                                    targetRdfList.add(createTarget(targetElement.getAttributes().item(0).getNodeValue()));
                                } catch (ArrayIndexOutOfBoundsException | DOMException e) {
                                    annotationError = true;
                                    LogFile.OUT.error("{} - Error processing Image Target for Annotation {} for resource with URL {}. "
                                            + "Skipping annotation", path, id, entityText, e);
                                    break;
                                }
                            }
                        }
                    }
                }
                if (!annotationError){
                    if (isPageAnnotation) {
                        if (StringUtils.isNotBlank(resLang)){
                            pageAnnotationRdf = new AnnotationRdf(id, dcType, motiv, resLang, targetRdfList);
                        } else {
                            pageAnnotationRdf = new AnnotationRdf(id, dcType, motiv, targetRdfList);
                        }
                    } else {
                        if (StringUtils.isNotBlank(resLang)){
                            annoList.add(createAnnotation(specRes, id, dcType, motiv, resLang, resource, targetRdfList));
                        } else {
                            annoList.add(createAnnotation(specRes, id, dcType, motiv, resource, targetRdfList));
                        }
                    }
                }
            }

            ap = new AnnoPageRdf(pageId, ftResource, ftText, ftLang, imgTargetBase, pageAnnotationRdf, annoList);
            LogFile.OUT.debug("{} - OK", path);
        } catch (Exception e) {
            LogFile.OUT.error("{} - Error processing page {} for resource with URL {}.", path, pageId, entityText, e);
        }
        return ap;
    }

    private static TargetRdf createTarget(String url) {
        Integer x = Integer.parseInt(StringUtils.split(StringUtils.splitByWholeSeparator(url, XYWHPOS)[1], ",")[0]);
        Integer y = Integer.parseInt(StringUtils.split(StringUtils.splitByWholeSeparator(url, XYWHPOS)[1], ",")[1]);
        Integer w = Integer.parseInt(StringUtils.split(StringUtils.splitByWholeSeparator(url, XYWHPOS)[1], ",")[2]);
        Integer h = Integer.parseInt(StringUtils.split(StringUtils.splitByWholeSeparator(url, XYWHPOS)[1], ",")[3]);
        return new TargetRdf(x, y, w, h);
    }

    private static AnnotationRdf createAnnotation(String specRes,
                                                  String id,
                                                  String dcType,
                                                  String motiv,
                                                  String lang,
                                                  String resource,
                                                  List   targetRdfList) {
        AnnotationRdf annoRdf = createAnnotation(specRes, id, dcType, motiv, resource, targetRdfList);
        annoRdf.setLang(lang);
        return annoRdf;
    }


    private static AnnotationRdf createAnnotation(String specRes,
                                                  String id,
                                                  String dcType,
                                                  String motiv,
                                                  String resource,
                                                  List   targetRdfList) {
        Integer from = Integer.parseInt(StringUtils.split(StringUtils.splitByWholeSeparator(specRes, CHARPOS)[1],
                                                          ",")[0]);
        Integer to   = Integer.parseInt(StringUtils.split(StringUtils.splitByWholeSeparator(specRes, CHARPOS)[1],
                                                          ",")[1]);
        return new AnnotationRdf(id, dcType, motiv, from, to, targetRdfList);
    }

    private static String readEntityString(String whichEntity, String internalSubset) {
        Pattern entityPattern = Pattern.compile("ENTITY\\s*?" + whichEntity + "\\s*?'(.+?)'");
        Matcher entityMatcher = entityPattern.matcher(StringUtils.replaceChars(internalSubset, '"', '\''));
        if (entityMatcher.find()) {
            return convertXmlEscapeCharacters(entityMatcher.group(1));
        } else {
            return null;
        }
    }

    /**
     * This methods converts all 5 xml escape character to their normal representation
     * @return
     */
    private static String convertXmlEscapeCharacters(String xml) {
        return xml.replaceAll("&lt;", "<")
                  .replaceAll("&gt;", ">")
                  .replaceAll("&quot;", "\"")
                  .replaceAll("&amp;","&")
                  .replaceAll("&apos;", "\'");
    }

    private static String readFileContents(Path file) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(file));
        } catch (IOException e) {
            LogFile.OUT.error("{} - I/O error reading file", file , e);
        }
        return content;
    }

}
