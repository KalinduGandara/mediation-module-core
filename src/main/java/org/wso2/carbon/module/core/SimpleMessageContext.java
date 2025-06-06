/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.module.core;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.collect.Iterators;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.OMXMLBuilderFactory;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.util.xpath.SynapseJsonPath;
import org.jaxen.JaxenException;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import org.wso2.carbon.module.core.collectors.CsvCollector;
import org.wso2.carbon.module.core.collectors.JsonArrayCollector;
import org.wso2.carbon.module.core.collectors.JsonObjectCollector;
import org.wso2.carbon.module.core.exceptions.SimpleMessageContextException;
import org.wso2.carbon.module.core.models.CsvPayloadInfo;
import org.wso2.carbon.module.core.models.IndexedElement;
import org.wso2.carbon.module.core.models.IndexedEntry;
import org.wso2.carbon.module.core.models.XmlNamespace;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.apache.synapse.util.PayloadHelper.TEXTELT;
import static org.apache.synapse.util.PayloadHelper.getXMLPayload;

public final class SimpleMessageContext {

    private static final Log log = LogFactory.getLog(SimpleMessageContext.class);
    private final MessageContext messageContext;
    private final JsonParser jsonParser;
    private final Gson gson;

    SimpleMessageContext(MessageContext messageContext) {

        this.messageContext = messageContext;
        gson = new Gson();
        jsonParser = new JsonParser();
    }

    /**
     * Returns the String representation of the Json payload
     * If payload is invalid, then gives an empty Json object
     *
     * @return payload string
     */
    public String getJsonString() {

        return JsonUtil.jsonPayloadToString(((Axis2MessageContext) messageContext).getAxis2MessageContext());
    }

    /**
     * Returns the Json payload as a JsonElement
     * If payload is null or not the Json content type, then give an empty Json Object as JsonElement
     *
     * @return payload body as JsonElement
     */
    public JsonElement getJsonElement() {

        String jsonPayloadString = getJsonString();
        JsonElement jsonElement;
        try {
            jsonElement = jsonParser.parse(jsonPayloadString);
        } catch (JsonSyntaxException e) {
            log.error("Invalid json", e);
            jsonElement = new JsonObject();
        }
        return jsonElement;
    }

    /**
     * Applies the given jsonPath to the context and returns result as a JsonElement
     * If json path is not valid or payload is invalid, gives an empty json object
     *
     * @param jsonPath Json path string to apply
     * @return matching Json content as JsonElement
     */
    public JsonElement getJsonElement(String jsonPath) {

        try {
            SynapseJsonPath synapseJsonPath = new SynapseJsonPath(jsonPath);
            Object evaluationResult = synapseJsonPath.evaluate(messageContext);
            return gson.toJsonTree(evaluationResult);
        } catch (JaxenException | PathNotFoundException e) {
            log.error("Error converting data", e);
            return new JsonObject();
        }
    }

    /**
     * Returns the Json Payload as a JsonArray
     * If payload is not a valid Json Array, returns an empty JsonArray
     *
     * @return payload as JsonArray
     */
    public JsonArray getJsonArray() {

        JsonElement jsonElement = getJsonElement();
        if (jsonElement.isJsonArray()) {
            return jsonElement.getAsJsonArray();
        } else {
            log.error("Error converting data : not a valid Json Array");
            return new JsonArray();
        }
    }

    /**
     * Returns a JsonArray by applying given json path to the payload
     * If json path is not valid or payload is not a valid json type, then returns an empty JsonArray
     *
     * @param jsonPath Json path String to apply on the payload
     * @return Result as a JsonArray
     */
    public JsonArray getJsonArray(String jsonPath) {

        JsonElement jsonPathResult = getJsonElement(jsonPath);

        JsonArray jsonArrayToStream;
        if (jsonPathResult.isJsonArray()) {
            jsonArrayToStream = jsonPathResult.getAsJsonArray();
        } else {
            log.error("Error converting data : not a valid Json Array");
            jsonArrayToStream = new JsonArray();
        }
        return jsonArrayToStream;
    }

    /**
     * Returns the Json payload as a JsonObject
     * If payload is null or not the Json content type, then returns an empty JsonObject
     *
     * @return payload as a JsonObject
     */
    public JsonObject getJsonObject() {

        JsonElement jsonElement = getJsonElement();
        if (jsonElement.isJsonObject()) {
            return jsonElement.getAsJsonObject();
        } else {
            log.error("Error converting data : not a valid Json Object");
            return new JsonObject();
        }
    }

    /**
     * Returns a JsonObject from the given json path applied to the payload.
     * If result is invalid then returns an empty JsonObject
     *
     * @param jsonPath json path string to apply
     * @return result JsonObject
     */
    public JsonObject getJsonObject(String jsonPath) {

        JsonElement jsonPathResult = getJsonElement(jsonPath);

        JsonObject jsonObject;
        if (jsonPathResult.isJsonArray()) {
            jsonObject =
                    jsonParser.parse(jsonPathResult.getAsJsonArray().get(0).getAsString()).getAsJsonObject();
        } else {
            jsonObject = jsonPathResult.getAsJsonObject();
        }
        return jsonObject;
    }

    /**
     * Returns the payload as a Stream of JsonElement if the payload is a valid Json array
     * or returns Empty stream
     *
     * @return Stream of JsonElement
     */
    public Stream<JsonElement> getJsonArrayStream() {

        JsonArray jsonArray = getJsonArray();
        return getJsonArrayStream(jsonArray);
    }

    /**
     * Returns a Stream of JsonElement from given JsonArray
     *
     * @param jsonArray JsonArray to convert to Stream
     * @return Stream of JsonElement
     */
    public Stream<JsonElement> getJsonArrayStream(JsonArray jsonArray) {

        return StreamSupport.stream(jsonArray.spliterator(), false);
    }

    /**
     * Returns a Stream of JsonElement if the matching part of payload for the given json path is a valid Json Array.
     * If not, returns an empty Stream.
     *
     * @param jsonPath Json path String to apply on payload
     * @return JsonElement Stream
     */
    public Stream<JsonElement> getJsonArrayStream(String jsonPath) {

        JsonArray jsonArrayToStream = getJsonArray(jsonPath);
        return getJsonArrayStream(jsonArrayToStream);
    }

    /**
     * Returns a Stream of IndexedElement of JsonElement from the payload of Json Array.
     * If payload is not a valid Json Array, returns an empty Stream
     *
     * @return Stream of IndexedJsonElement
     */
    public Stream<IndexedElement<JsonElement>> getJsonArrayStreamWithIndex() {

        JsonArray jsonArray = getJsonArray();
        return getJsonArrayStreamWithIndex(jsonArray);
    }

    /**
     * Returns a Stream of IndexedElement of JsonElement from given JsonArray
     *
     * @param jsonArray JsonArray to convert to Stream
     * @return Stream of IndexedJsonElement
     */
    public Stream<IndexedElement<JsonElement>> getJsonArrayStreamWithIndex(JsonArray jsonArray) {

        return IntStream.range(0, jsonArray.size())
                .mapToObj(i -> new IndexedElement<>(i, jsonArray.get(i)));
    }

    /**
     * Returns a Stream of IndexedElement of JsonElement if the matching part of payload for the given json path is a
     * valid Json Array.
     * If not, returns as empty stream.
     *
     * @param jsonPath Json path String to apply on payload
     * @return Stream of IndexedElement of JsonElement
     */
    public Stream<IndexedElement<JsonElement>> getJsonArrayStreamWithIndex(String jsonPath) {

        JsonArray jsonArrayToStream = getJsonArray(jsonPath);
        return getJsonArrayStreamWithIndex(jsonArrayToStream);
    }

    /**
     * Returns the payload as a stream of Object entries (key, value)
     * If payload is not a valid JsonObject then, empty Stream would return.
     *
     * @return Stream of Map.Entry with object attribute name as the key
     * and attribute value as value
     */
    public Stream<Map.Entry<String, JsonElement>> getJsonObjectStream() {

        JsonObject jsonObject = getJsonObject();
        return getJsonObjectStream(jsonObject);
    }

    /**
     * Returns the given JsonObject as a stream of Object entries (key, value)
     *
     * @param jsonObject JsonObject to convert as a Stream
     * @return Stream of Map.Entry with object attribute name as the key
     * and attribute value as value
     */
    public Stream<Map.Entry<String, JsonElement>> getJsonObjectStream(JsonObject jsonObject) {

        return jsonObject.entrySet().stream();
    }

    /**
     * Returns an Object stream for the Json payload after applying the given json path.
     * If payload is not valid or the json path does not match,
     * returns a empty Stream
     *
     * @param jsonPath json path string to apply
     * @return Object stream
     */
    public Stream<Map.Entry<String, JsonElement>> getJsonObjectStream(String jsonPath) {

        JsonObject jsonObjectToStream = getJsonObject(jsonPath);
        return getJsonObjectStream(jsonObjectToStream);
    }

    /**
     * Returns the root json object as a Stream of IndexedEntry. IndexedEntry includes the position of the parameter
     * as index
     *
     * @return Stream of IndexedEntry
     */
    public Stream<IndexedEntry> getJsonObjectStreamWithIndex() {

        JsonObject jsonObject = getJsonObject();
        return getJsonObjectStreamWithIndex(jsonObject);
    }

    /**
     * Returns the given JsonObject as a Stream of IndexedEntry. IndexedEntry includes the position of the parameter as
     * index
     *
     * @param jsonObject JsonObject to convert as a Stream
     * @return Stream of IndexedEntry
     */
    public Stream<IndexedEntry> getJsonObjectStreamWithIndex(JsonObject jsonObject) {

        final Iterator<Map.Entry<String, JsonElement>> iterator = jsonObject.entrySet().iterator();
        return IntStream.range(0, jsonObject.entrySet().size())
                .mapToObj(i -> new IndexedEntry(i, iterator.next()));
    }

    /**
     * Returns an Indexed Object stream for the Json payload after applying the given json path.
     * If payload is not valid or the json path does not match,
     * returns an empty Stream
     *
     * @param jsonPath json path string to apply
     * @return Indexed Object stream
     */
    public Stream<IndexedEntry> getJsonObjectStreamWithIndex(String jsonPath) {

        JsonObject jsonObjectToStream = getJsonObject(jsonPath);
        return getJsonObjectStreamWithIndex(jsonObjectToStream);
    }

    /**
     * Set the given JsonElement as the Json payload of. If the current payload type is not Json, the payload type would
     * be set as Json
     *
     * @param jsonPayload JsonElement to set as the current payload
     */
    public void setJsonPayload(JsonElement jsonPayload) {

        String transformedJson = jsonPayload.toString();
        setJsonPayload(transformedJson);
    }

    /**
     * Set the given String as the Json payload of. If the current payload type is not Json, the payload type would be
     * set to Json
     *
     * @param payload String to set as the current payload
     */
    public void setJsonPayload(String payload) {

        setPayloadType("application/json");

        try {
            JsonUtil.getNewJsonPayload(((Axis2MessageContext) messageContext).getAxis2MessageContext(),
                    payload, true, true);
        } catch (AxisFault axisFault) {
            throw new SimpleMessageContextException(axisFault);
        }
    }

    /**
     * Returns the root xml element of the payload
     *
     * @return root xml element as OMElement
     */
    public OMElement getRootXmlElement() {

        return messageContext.getEnvelope().getBody().getFirstElement();
    }

    /**
     * Replace toot XML element with the given OMElement
     *
     * @param element OMElement to replace root element
     */
    public void replaceRootXmlElement(OMElement element) {

        messageContext.getEnvelope().getBody().getFirstElement().detach();
        messageContext.getEnvelope().getBody().addChild(element);
        setPayloadType("application/xml");
    }

    /**
     * Return List of OMElement by applying given x path Strin to the root XML Element
     * If payload is not valid or error in applying xPath, returns an empty List
     *
     * @param xPath      X path String to apply
     * @param namespaces Namespaces to register if XML has namespaces
     * @return List of OMElement matching the X path
     */
    public List<OMElement> getXmlElements(String xPath, XmlNamespace... namespaces) {

        return getXmlElements(getRootXmlElement(), xPath, namespaces);
    }

    /**
     * Return List of OMElement by applying given X path String to the given XML Element.
     * If payload is not valid or error in applying xPath, returns an empty List
     *
     * @param baseElement Base element to apply X path
     * @param xPath       X path String to apply
     * @param namespaces  Namespaces to register if XML has namespaces
     * @return List of OMElement matching the X path
     */
    public List<OMElement> getXmlElements(OMElement baseElement, String xPath, XmlNamespace... namespaces) {

        try {
            AXIOMXPath axiomxPath = new AXIOMXPath(xPath);
            if (namespaces != null) {
                for (XmlNamespace namespace : namespaces) {
                    axiomxPath.addNamespace(namespace.getPrefix(), namespace.getUri());
                }
            }

            return axiomxPath.selectNodes(baseElement);
        } catch (JaxenException e) {
            log.error("XML parsing error", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns a Stream of OMElement by applying given xPath to the root of XML payload.
     * If error occur while applying xPath, an empty Stream would return
     *
     * @param xPath      xPath String to apply
     * @param namespaces Namespaces to register if XML has namespaces
     * @return Stream of OMElement
     */
    public Stream<OMElement> getXmlElementsStream(String xPath, XmlNamespace... namespaces) {

        return getXmlElements(xPath, namespaces).stream();
    }

    /**
     * Returns a Stream of OMElement by applying given xPath to given OMElement as root,
     * If error occur while applying xPath, an empty Stream would return
     *
     * @param baseElement OMElement to consider as root element
     * @param xPath       xPath string to apply
     * @param namespaces  Namespaces to register if XML has namespaces
     * @return Stream of OMElement
     */
    public Stream<OMElement> getXmlElementsStream(OMElement baseElement, String xPath, XmlNamespace... namespaces) {

        return getXmlElements(baseElement, xPath, namespaces).stream();
    }

    /**
     * If the payload is type XML, then returns the child elements of the root XML element as a Stream of OMElement
     *
     * @return Stream of OMElement of the child elements of the root xml element
     */
    public Stream<OMElement> getXmlChildElementsStream() {

        OMElement rootElement = messageContext.getEnvelope().getBody().getFirstElement();
        return getXmlChildElementsStream(rootElement);
    }

    /**
     * Returns the child elements of the given OMElement as Stream of OMElements
     *
     * @param xmlElement parent OMElement to get child elements
     * @return Stream of OMElement
     */
    public Stream<OMElement> getXmlChildElementsStream(OMElement xmlElement) {

        Iterable<OMElement> iterable = xmlElement::getChildElements;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * If the payload is type XML, then returns the child elements of the root XML element as a Stream of
     * IndexedElement of OMElement
     *
     * @return Stream of IndexedElement of OMElement
     */
    public Stream<IndexedElement<OMElement>> getXmlChildElementsStreamWithIndex() {

        OMElement rootElement = messageContext.getEnvelope().getBody().getFirstElement();
        return getXmlChildElementsStreamWithIndex(rootElement);
    }

    /**
     * Return a Stream of IndexedElement of OMElement for the given OMElement children
     *
     * @param xmlElement OMElement to consider as the root element to give child stream
     * @return Stream of IndexedElement of OMElement
     */
    public Stream<IndexedElement<OMElement>> getXmlChildElementsStreamWithIndex(OMElement xmlElement) {

        int size = Iterators.size(xmlElement.getChildElements());
        final Iterator<OMElement> iterator = xmlElement.getChildElements();
        return IntStream.range(0, size)
                .mapToObj(i -> new IndexedElement<>(i, iterator.next()));
    }

    /**
     * Return a Stream of IndexedElement of OMElement for the result of given xPath applied to the root XML element.
     *
     * @param xPath      xPath String to apply
     * @param namespaces Namespaces to register if XML has namespaces
     * @return Stream of IndexedElement of OMElement
     */
    public Stream<IndexedElement<OMElement>> getXmlElementsStreamWithIndex(String xPath, XmlNamespace... namespaces) {

        List<OMElement> xmlElements = getXmlElements(xPath, namespaces);
        if (!xmlElements.isEmpty()) {
            return IntStream.range(0, xmlElements.size())
                    .mapToObj(i -> new IndexedElement<>(i, xmlElements.get(i)));
        } else {
            return Stream.empty();
        }
    }

    /**
     * Returns a Stream of IndexedElement of OMElement by applying given xPath to the given OMElement
     *
     * @param baseElement OMElement to apply xPath
     * @param xPath       xPath String to apply
     * @param namespaces  Namespaces to register if XML has namespaces
     * @return Stream of IndexedElement of OMElement
     */
    public Stream<IndexedElement<OMElement>> getXmlElementsStreamWithIndex(OMElement baseElement, String xPath,
                                                                           XmlNamespace... namespaces) {

        List<OMElement> elementList = getXmlElements(baseElement, xPath, namespaces);
        return IntStream.range(0, elementList.size())
                .mapToObj(i -> new IndexedElement<>(i, elementList.get(i)));
    }

    /**
     * Return a Stream of OMElement for the CData content matching the given xPath ( considering CData content is a XML
     * element)
     *
     * @param xPath      xPath String to apply
     * @param namespaces Namespaces to register if XML has namespaces
     * @return Stream of OMElement
     */
    public Stream<OMElement> getCDataContentXmlStream(String xPath, XmlNamespace... namespaces) {

        String cDataContent = getCDataContent(xPath, namespaces);

        if (cDataContent == null) {
            return Stream.empty();
        }

        OMElement cDataXML = OMXMLBuilderFactory.createOMBuilder(new StringReader(cDataContent)).getDocumentElement();
        return getXmlChildElementsStream(cDataXML);

    }

    /**
     * Return a Stream of JsonElement for the CData content matching the given XPath ( considering CData content is a
     * Json array )
     *
     * @param xPath      xPath String to apply
     * @param namespaces Namespaces to register if XML has namespaces
     * @return Stream of JsonElement
     */
    public Stream<JsonElement> getCDataContentJsonArrayStream(String xPath, XmlNamespace... namespaces) {

        String cDataContent = getCDataContent(xPath, namespaces);

        if (cDataContent == null) {
            return Stream.empty();
        }

        JsonElement jsonElement = jsonParser.parse(cDataContent);
        if (!jsonElement.isJsonArray()) {
            log.error("Invalid json array");
            return Stream.empty();
        }

        return getJsonArrayStream(jsonElement.getAsJsonArray());
    }

    /**
     * Get content of a CData content in XML
     *
     * @param xPath      xPath for the CData section
     * @param namespaces namespaces to apply with the xPath
     * @return CData content as a String
     */
    private String getCDataContent(String xPath, XmlNamespace... namespaces) {

        List<OMElement> results = getXmlElements(xPath, namespaces);

        if (results.isEmpty()) {
            log.error("No results for xPath");
            return null;
        }

        OMElement cDataElement = results.get(0);
        return cDataElement.getText();
    }

    /**
     * If payload type is Text, then return the text payload as String.
     * If error occur, then returns an empty String
     *
     * @return Payload String
     */
    public String getTextPayload() {

        return getTextPayload(messageContext.getEnvelope());
    }

    /**
     * Set the current payload to the given text
     *
     * @param text String to set as the current payload
     */
    public void setTextPayload(String text) {

        setPayloadType("text/plain");

        if (messageContext.getEnvelope() == null) {
            try {
                messageContext.setEnvelope(OMAbstractFactory.getSOAP12Factory()
                        .createSOAPEnvelope());
            } catch (Exception e) {
                throw new SimpleMessageContextException(e);
            }
        }
        org.apache.synapse.util.PayloadHelper.setTextPayload(messageContext.getEnvelope(), text);
    }

    /**
     * Get text payload from a SOAP envelope
     *
     * @param envelope SOAP envelop to get payload
     * @return Payload text
     */
    private String getTextPayload(SOAPEnvelope envelope) {

        OMElement el = getXMLPayload(envelope);
        if (el == null) {
            return "";
        } else if (!el.getQName().equals(TEXTELT)) {
            log.error("Wrong QName " + el.getQName());
            return "";
        } else {
            return getTextValue(el);
        }
    }

    /**
     * Get text value from OMNode
     *
     * @param node OMNode to get text value
     * @return text value of the node
     */
    private String getTextValue(OMNode node) {

        switch (node.getType()) {
            case OMNode.ELEMENT_NODE:
                StringBuilder sb = new StringBuilder();
                Iterator<OMNode> children = ((OMElement) node).getChildren();
                while (children.hasNext()) {
                    sb.append(getTextValue(children.next()));
                }
                return sb.toString();
            case OMNode.TEXT_NODE:
                String text = ((OMText) node).getText();
                return StringEscapeUtils.escapeXml11(text);
            default:
                return "";
        }
    }

    /**
     * Set given payload as CSV
     *
     * @param separator Separator to use for csv payload
     * @param data      List of String array representing rows of CSV
     */
    public void setCsvPayload(List<String[]> data, char separator) {

        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter,
                separator,
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);

        csvWriter.writeAll(data);
        try {
            csvWriter.close();
            stringWriter.flush();
            String resultPayload = stringWriter.toString();
            setTextPayload(resultPayload);
        } catch (IOException e) {
            throw new SimpleMessageContextException(e);
        }
    }

    /**
     * Return String[] representation for CSV payload
     *
     * @return String[] representation of the CSV payload
     */
    public List<String[]> getCsvPayload() {

        return getCsvPayload(0);
    }

    /**
     * Set given payload as CSV
     *
     * @param data List of String array representing rows of CSV
     */
    public void setCsvPayload(List<String[]> data) {

        setCsvPayload(data, CSVWriter.DEFAULT_SEPARATOR);
    }

    /**
     * Return String[] representation for CSV payload
     *
     * @param linesToSkip Number of lines to skip from header of CSV
     * @return String[] representation of the CSV payload
     */
    public List<String[]> getCsvPayload(int linesToSkip) {

        return getCsvPayload(linesToSkip, CSVReader.DEFAULT_SEPARATOR);
    }

    /**
     * Returns String[] representation for CSV payload
     *
     * @param linesToSkip Number of lines to skip from the header of the CSV
     * @param separator   Separator to use in the CSV
     * @return String[] representation of the CSV payload
     */
    public List<String[]> getCsvPayload(int linesToSkip, char separator) {

        String payloadText = getTextPayload();

        String csvText;
        if (payloadText != null) {
            csvText = StringEscapeUtils.unescapeXml(payloadText);
        } else {
            log.error("Error converting data : not a valid CSV payload");
            csvText = "";
        }
        CSVReader csvReader =
                new CSVReader(new StringReader(csvText), separator, CSVReader.DEFAULT_QUOTE_CHARACTER,
                        linesToSkip);
        try {
            return csvReader.readAll();
        } catch (Exception e) {
            throw new SimpleMessageContextException("Error reading csv payload", e);
        }
    }

    /**
     * Returns information about the CSV payload
     *
     * @param separator Character separator for CSV
     * @return CsvPayloadInfo for the current payload
     */
    public CsvPayloadInfo getCsvPayloadInfo(char separator) {

        List<String[]> csvPayload = getCsvPayload(0, separator);
        CsvPayloadInfo payloadInfo;

        if (!csvPayload.isEmpty()) {
            String[] firstRow = csvPayload.get(0);
            int numberOfColumns = firstRow.length;
            payloadInfo = new CsvPayloadInfo(firstRow, numberOfColumns, csvPayload);
        } else {
            payloadInfo = new CsvPayloadInfo(new String[]{}, 0, Collections.emptyList());
        }

        return payloadInfo;
    }

    /**
     * If the payload is a CSV, returns the payload as a Stream of Array of String for each line in CSV content.
     * If payload is not a valid CSV then, returns an empty Stream
     *
     * @return Stream of Array of String representing each line of the CSV payload
     */
    public Stream<String[]> getCsvArrayStream() {

        return getCsvArrayStream(0);
    }

    /**
     * If the payload is a CSV, returns the payload as a Stream of Array of String for each line in CSV content.
     * If payload is not a valid CSV then, returns an empty Stream
     *
     * @param linesToSkip Number of lines to skip from header of CSV
     * @return Stream of Array of String representing each line of the CSV payload
     */
    public Stream<String[]> getCsvArrayStream(int linesToSkip) {

        return getCsvArrayStream(linesToSkip, CSVReader.DEFAULT_SEPARATOR);
    }

    /**
     * If the payload is a CSV, returns the payload as a Stream of Array of String for each line in CSV content.
     * If payload is not a valid CSV then, returns an empty Stream
     *
     * @param linesToSkip Number of lines to skip from header of CSV
     * @param separator   Separator to use in the CSV
     * @return Stream of Array of String representing each line of the CSV payload
     */
    public Stream<String[]> getCsvArrayStream(int linesToSkip, char separator) {

        List<String[]> rows = getCsvPayload(linesToSkip, separator);
        return rows.stream();
    }

    /**
     * If the payload is a CSV, returns the payload as a Stream of IndexedElement of String[] for lines in CSV content.
     *
     * @return Stream of IndexedElement of String[]
     */
    public Stream<IndexedElement<String[]>> getCsvArrayStreamWithIndex() {

        return getCsvArrayStreamWithIndex(0);
    }

    /**
     * If the payload is a CSV, returns the payload as a Stream of IndexedElement of String[] for lines in CSV content.
     *
     * @param linesToSkip Number of lines to skip from header of CSV
     * @return Stream of IndexedElement of String[]
     */
    public Stream<IndexedElement<String[]>> getCsvArrayStreamWithIndex(int linesToSkip) {

        return getCsvArrayStreamWithIndex(linesToSkip, CSVReader.DEFAULT_SEPARATOR);
    }

    /**
     * If the payload is a CSV, returns the payload as a Stream of IndexedElement of String[] for lines in CSV content.
     *
     * @param linesToSkip Number of lines to skip from header of CSV
     * @param separator   Separator of the CSV payload
     * @return Stream of IndexedElement of String[]
     */
    public Stream<IndexedElement<String[]>> getCsvArrayStreamWithIndex(int linesToSkip, char separator) {

        List<String[]> rows = getCsvPayload(linesToSkip, separator);
        return IntStream.range(0, rows.size())
                .mapToObj(i -> new IndexedElement<>(i, rows.get(i)));
    }

    /**
     * Returns the value of a custom (local) property set on the message instance
     *
     * @param key key to look up property
     * @return value for the given key
     */
    public Object getProperty(String key) {

        return messageContext.getProperty(key);
    }

    /**
     * Set a custom (local) property with the given name on the message instance
     *
     * @param key   key to be used
     * @param value value to be saved
     */
    public void setProperty(String key, Object value) {

        messageContext.setProperty(key, value);
    }

    /**
     * Set given key and value as a header
     *
     * @param key   header key
     * @param value header value
     */
    public void setHeader(String key, String value) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        Object headerObj = axis2MessageContext.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

        if (headerObj != null) {
            Map headers = (Map) headerObj;
            headers.remove(key);
            headers.put(key, value);
        }
    }

    /**
     * Returns an instance of JsonArrayCollector to collect and set payload as JsonArray
     *
     * @param objectKey The result json array would be inserted in to a wrapper json object with this key
     * @return instance of JsonArrayCollector
     */
    public JsonArrayCollector collectToJsonArray(String objectKey) {

        return new JsonArrayCollector(this, objectKey);
    }

    /**
     * Returns an instance of JsonArrayCollector to collect and set payload as JsonArray
     *
     * @return instance of JsonArrayCollector
     */
    public JsonArrayCollector collectToJsonArray() {

        return new JsonArrayCollector(this, null);
    }

    /**
     * Returns an instance of JsonObjectCollector to collect and set payload as JsonObject
     *
     * @return instance of JsonObjectCollector
     */
    public JsonObjectCollector collectToJsonObject() {

        return new JsonObjectCollector(this);
    }

    /**
     * Returns an instance of CsvCollector to collect and set payload as CSV for String[] Stream
     *
     * @return instance of CsvCollector
     */
    public CsvCollector collectToCsv() {

        return new CsvCollector(this, null);
    }

    /**
     * Returns an instance of CsvCollector to collect and set payload as CSV for String[] Stream
     *
     * @param header Header to set for CSV
     * @return instance of CsvCollector
     */
    public CsvCollector collectToCsv(String[] header) {

        return new CsvCollector(this, header);
    }

    /**
     * Returns an instance of CsvCollector to collect and set payload as CSV for String[] Stream
     *
     * @param header    Header to set for CSV
     * @param separator Separator to use in the CSV payload
     * @return instance of CsvCollector
     */
    public CsvCollector collectToCsv(String[] header, char separator) {

        return new CsvCollector(this, header, separator);
    }


    /**
     * Returns an instance of CsvCollector to collect and set payload as CSV for String[] Stream
     *
     * @param header              Header to set for CSV
     * @param suppressEscapeChar  Whether escape Characters will be suppressed in the output CSV
     * @return instance of CsvCollector
     */
    public CsvCollector collectToCsv(String[] header, boolean suppressEscapeChar) {

        return new CsvCollector(this, header, CSVWriter.DEFAULT_SEPARATOR, suppressEscapeChar);
    }

    /**
     * Returns an instance of CsvCollector to collect and set payload as CSV for String[] Stream
     *
     * @param header             Header to set for CSV
     * @param suppressEscapeChar Whether escape Characters will be suppressed in the output CSV
     * @param applyQuotes        Whether to apply quotes for the Fields with line breaks, commas, or double quotes
     * @return instance of CsvCollector
     */
    public CsvCollector collectToCsv(String[] header, boolean suppressEscapeChar, boolean applyQuotes) {

        return new CsvCollector(this, header, CSVWriter.DEFAULT_SEPARATOR, suppressEscapeChar, applyQuotes);
    }

    /**
     * Returns an instance of CsvCollector to collect and set payload as CSV for String[] Stream
     *
     * @param header    Header to set for CSV
     * @param separator Separator to use in the CSV payload
     * @param suppressEscapeChar  Whether escape Characters will be suppressed in the output CSV
     * @return instance of CsvCollector
     */
    public CsvCollector collectToCsv(String[] header, char separator, boolean suppressEscapeChar) {

        return new CsvCollector(this, header, separator, suppressEscapeChar);
    }

    /**
     * Returns an instance of CsvCollector to collect and set payload as CSV for String[] Stream
     *
     * @param header             Header to set for CSV
     * @param separator          Separator to use in the CSV payload
     * @param suppressEscapeChar Whether escape Characters will be suppressed in the output CSV
     * @param applyQuotes        Whether to apply quotes for the Fields with line breaks, commas, or double quotes
     * @return instance of CsvCollector
     */
    public CsvCollector collectToCsv(String[] header, char separator, boolean suppressEscapeChar, boolean applyQuotes) {

        return new CsvCollector(this, header, separator, suppressEscapeChar, applyQuotes);
    }

    /**
     * Set payload type to given type
     *
     * @param payloadType payload type to set
     */
    private void setPayloadType(String payloadType) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        axis2MessageContext.setProperty(Constants.Configuration.MESSAGE_TYPE, payloadType);
        axis2MessageContext.setProperty(Constants.Configuration.CONTENT_TYPE, payloadType);
    }

    /**
     * Get template parameters when using in a connector
     *
     * @param paramName parameter index
     * @return parameter content
     */
    public Object lookupTemplateParameter(String paramName) {

        return ConnectorUtils.lookupTemplateParamater(messageContext, paramName);
    }

    /**
     * Get template parameters when using in a connector
     *
     * @param index parameter index
     * @return parameter content
     */
    public Object lookupTemplateParameter(int index) {

        return ConnectorUtils.lookupTemplateParamater(messageContext, index);

    }

    /**
     * Return current MessageContext
     *
     * @return current MessageContext
     */
    MessageContext getMessageContext() {

        return messageContext;
    }

}
