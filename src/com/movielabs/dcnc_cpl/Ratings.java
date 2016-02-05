/*
 * Copyright (c) 2015 MovieLabs
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.movielabs.dcnc_cpl;

import java.io.File;
import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

public class Ratings implements NamespaceContext {
    private DocumentBuilderFactory factory;
    private DocumentBuilder builder;
    private Document dom;

    public Ratings(String rf) {
        try{
            factory =  DocumentBuilderFactory.newInstance();
                
            //Configure the factory object
            factory.setValidating(false);
            factory.setNamespaceAware(true);
            builder = factory.newDocumentBuilder();
            dom = builder.parse(new File(rf));
        }catch(Exception e){
            e.printStackTrace(System.err);
        }//end catch
    }

    public Document getDOM() {
        return dom;
    }

    public String getNamespaceURI(String prefix) {
        if (prefix == null) throw new NullPointerException("Null prefix");
        else if (prefix.equals("mdcr")) return "http://www.movielabs.com/schema/mdcr/v1.1";
        else if (prefix.equals("md")) return "http://www.movielabs.com/schema/md/v2.1/md";
        return XMLConstants.NULL_NS_URI;
    }

    // This method isn't necessary for XPath processing.
    public String getPrefix(String uri) {
        throw new UnsupportedOperationException();
    }

    // This method isn't necessary for XPath processing either.
    public Iterator getPrefixes(String uri) {
        throw new UnsupportedOperationException();
    }

    public String findRating(String territory, String rating) throws XPathExpressionException {
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        xpath.setNamespaceContext((NamespaceContext) this);
        String xpe = String.format("/mdcr:RatingSystemSet/mdcr:RatingSystem/mdcr:RatingSystemID/mdcr:Region/md:country[text()='%s']", territory);
        XPathExpression expr = xpath.compile(xpe);
        
        NodeList nodeList = (NodeList)expr.evaluate(dom, XPathConstants.NODESET);
        //System.out.println("------------");
        for (int i=0; i<nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            expr = xpath.compile("mdcr:Rating");
            NodeList nl = (NodeList)(expr.evaluate(n.getParentNode().getParentNode().getParentNode(), XPathConstants.NODESET));
            for (int j=0; j<nl.getLength(); j++) {
                n = nl.item(j);
                String tag = n.getNodeName();
                if (tag.equals("mdcr:Rating")) {
                    NamedNodeMap nnm = n.getAttributes();
                    Node rv = nnm.getNamedItem("ratingID");
                    String r = rv.getNodeValue();
                    if (r.equals(rating)) {
                        //System.out.println("   rating='" + rating + "' r='" + r + "'");
                        expr = xpath.compile("mdcr:URI/text()");
                        n = (Node)expr.evaluate(n, XPathConstants.NODE);
                        Text tmp = (Text) n;
                        return tmp.getWholeText();
                    }
                }
            }
        }        
        return "";
    }
}
