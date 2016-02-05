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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;


public class DigitalCinemaName {
    private boolean verbose = false;
    private Document dom = null;
    private Element root;

    private String dcncOriginal, dcncTitle, dcncContentType, dcncAspect, dcncRatio,
        dcncLanguage, dcncTerritory, dcncRating, dcncRatingURI, dcncAudio, dcncResolution,
        dcncStudio, dcncDate, dcncFacility, dcncStandard, dcncPackage;
    private static final String[] ISO3166 = Locale.getISOCountries();

    private String[] lang = new String[3];
    private String yyyy, mm, dd;
    private String contentKind;
    private String contentVersion = "1";
    private boolean badISO3166 = false;
    private boolean optTemp, optPre, optRedBand, opt3D;
    private String optLuminance, optFrameRate, optChain;

    private static Map<String, Integer> storeWidth;
    private static Map<String, Integer> storeHeight;

    static {
        storeWidth = new HashMap<String, Integer>();
        storeWidth.put("F2K", 1998);
        storeWidth.put("S2K", 2048);
        storeWidth.put("C2K", 2048);
        storeWidth.put("F4K", 3996);
        storeWidth.put("S4K", 4096);
        storeWidth.put("C4K", 4096);
 
        storeHeight = new HashMap<String, Integer>();
        storeHeight.put("F2K", 1080);
        storeHeight.put("S2K",  858);
        storeHeight.put("C2K", 1080);
        storeHeight.put("F4K", 2160);
        storeHeight.put("S4K", 1716);
        storeHeight.put("C4K", 2160);
    }

    private final static String[] field = {
        "Title", "ContentType", "AspectRatio", "Language", "Territory", "Audio",
        "Resolution", "Studio", "Date", "Facility", "Standard", "Package"};

    private void parseTitle(String s) {
        if (verbose)
            System.out.println("Title = " + s);
        dcncTitle = s;
    }

    private enum OPT {
        Temp,
        Pre,
        RedBand,
        CHAIN,
        D2orD3,
        Luminance,
        FrameRate
    };

    private String decodeLuminance(String l) {
        switch(l.length()) {
        case 1:
            return l;
		case 2:
            if (l.substring(0, 1).equals("1"))
                return l;
            else
                return l.substring(0,1) + "." + l.substring(1);
		default:
            System.out.println("Unknown luminance value: " + l);
            return l;
        }
    }

    private void parseContentType(String s) {
        if (verbose)
            System.out.println("Content Type = " + s);
        Pattern pat = Pattern.compile("^(FTR|TLR|TSR)-(\\d+)(?:-(.+))?$");
        Matcher m = pat.matcher(s);
        if (m.matches()) {
            switch(m.group(1)) {
            case "FTR":
                contentKind = "feature";
                break;
            case "TLR":
                contentKind = "trailer";
                break;
            case "TSR":
                contentKind = "teaser";
                break;
            case "PRO":
                System.out.println("no SMPTE 429-7 equivalent for 'PRO': substituting 'teaser'");
                contentKind = "teaser";
                break;
            case "TST":
                contentKind = "test";
                break;
            case "RTG":
                contentKind = "rating";
                break;
            case "SHR":
                contentKind = "short";
                break;
            case "ADV":
                contentKind = "advertisement";
                break;
            case "XSN":
                contentKind = "transitional";
                break;
            case "PSA":
                contentKind = "psa";
                break;
            case "POL":
                contentKind = "policy";
                break;
            default:
                contentKind = "???";
            } /* switch */
            contentVersion = m.group(2);
            if (m.group(3) != null) {
                String[] modifiers = m.group(3).split("-");
                OPT nextOpt = OPT.Temp;
                for (String opt : modifiers) {
                    switch(opt) {
                    case "Temp":
                        if (OPT.Temp.ordinal() < nextOpt.ordinal())
                            System.out.println("'Temp' Option out of order");
                        optTemp = true;
                        nextOpt = OPT.Pre;
                        break;
                    case "Pre":
                        if (OPT.Pre.ordinal() < nextOpt.ordinal())
                            System.out.println("'Pre' Option out of order");
                        optPre = true;
                        nextOpt = OPT.RedBand;
                        break;
                    case "RedBand":
                        if (OPT.RedBand.ordinal() < nextOpt.ordinal())
                            System.out.println("'RedBand' Option out of order");
                        optRedBand = true;
                        nextOpt = OPT.CHAIN;
                        break;
                    case "2D":
                        // Fallthrough
                    case "3D":
                        if (OPT.D2orD3.ordinal() < nextOpt.ordinal())
                            System.out.println("'2D/3D' Option out of order");
                        opt3D = opt.equals("3D");
                        nextOpt = OPT.Luminance;
                        break;
                    default:
                        pat = Pattern.compile("^(\\d+)fl$");
                        m = pat.matcher(opt);
                        if (m.matches()) { // Luminance
                            if (OPT.Luminance.ordinal() < nextOpt.ordinal())
                                System.out.println("'Luminance' Option out of order");
                            optLuminance = decodeLuminance(m.group(1));
                            nextOpt = OPT.FrameRate;
                        } else { // FrameRate
                            pat = Pattern.compile("^(\\d+)$");
                            m = pat.matcher(opt);
                            if (m.matches()) {
                                if (OPT.FrameRate.ordinal() < nextOpt.ordinal())
                                    System.out.println("'FrameRate' Option out of order");
                                optFrameRate = m.group(1);
                            } else { // Chain
                                if (OPT.CHAIN.ordinal() < nextOpt.ordinal())
                                    System.out.println("'CHAIN' Option out of order");
                                optChain = opt;
                                nextOpt = OPT.D2orD3;
                            }
                        }
                        break;
                    }
                } /* switch */
            } /* for */
        } else {
            System.out.println("bad content type: " + s);
        }
        dcncContentType = s;
    } /* parseContentType() */

    private void parseAspect(String s) {
        Pattern pat = Pattern.compile("^(F|S|C)(?:-(\\d{3}))?$");
        Matcher m = pat.matcher(s);
        if (m.matches()) {
            dcncAspect = m.group(1);
            if (m.group(2) != null)
                dcncRatio = m.group(2).substring(0, 1) + "." + m.group(2).substring(1);
        }
        if (verbose) {
            if (m.matches()) 
                System.out.println("Aspect: " + dcncAspect + " Ratio: " + dcncRatio);
            else
                System.out.println("Bad aspect ratio specification: " + s);
        }
    }

    private boolean parseLanguage(String s) {
        if (verbose)
            System.out.println("Language = " + s);
        Pattern pat = Pattern.compile("^([a-zA-Z]{2,3})(?:-([a-zA-Z]{2,3})(?:-([a-zA-Z]{2,3}))?)?$");
        Matcher m = pat.matcher(s);
        if (m.matches())
            for (int i=1; i<=m.groupCount(); i++) 
                if (m.group(i) != null)
                    lang[i-1] = m.group(i).toLowerCase();
        else
            return false;
        dcncLanguage = s;
        return true;
    }

    private String parseRating(String territory, String rating) throws XPathExpressionException {
        Ratings r = new Ratings("CMR_Ratings_v2.2.6.xml");
        Document rdom = r.getDOM();
        return r.findRating(territory, rating);
    }

    private void parseTerritory(String s) throws XPathExpressionException {
        String[] tr = s.split("-");
        dcncTerritory = tr[0];
        if (dcncTerritory.equals("UK")) {
            System.out.println("Warning: mapping UK (invalid ISO3166-1) to GB");
            dcncTerritory = "GB";
        }
        badISO3166 = Arrays.binarySearch(ISO3166, dcncTerritory) == -1;
        if (tr.length == 2) {
            dcncRating = tr[1];
            dcncRatingURI = parseRating(dcncTerritory, tr[1]);
        } else {
            dcncRating = null;
            dcncRatingURI = null;
        }
        if (verbose) {
            System.out.println("Territory: " + dcncTerritory + " Rating: " + dcncRating);
            if (badISO3166)
                System.out.println("Warning!  unknown territory: " + dcncTerritory);
        }
    }

    private Element mRating() throws Exception {
        String agencyName = null;
        Element e = dom.createElement("RatingList");
        if (dcncRating != null) {
            Element e2 = dom.createElement("Rating");
            e.appendChild(e2);
            e2.appendChild(mGenericElement("Agency", dcncRatingURI));
            // if (dcncTerritory.equals("US"))
            //     e2.appendChild(mGenericElement("Agency", "http://www.mpaa.org/2003-ratings"));
            // else
            //     e2.appendChild(mGenericElement("Agency", 
            //                       "unknown territory: " + dcncTerritory + ")"));
            e2.appendChild(mGenericElement("Label", dcncRating));
        }
        return e;
    }

    private void parseAudio(String s) {
        if (verbose)
            System.out.println("Audio = " + s);
        dcncAudio = s;
    }

    private void parseResolution(String s) {
        if (verbose)
            System.out.println("Resolution = " + s);
        if (!(s.equals("2K") || s.equals("4K")))
            System.out.println("Invalid resolution: " + s);
        dcncResolution = s;
    }

    private void parseStudio(String s) {
        if (verbose)
            System.out.println("Studio = " + s);
        dcncStudio = s;
    }

    private void parseDate(String s) {
        if (verbose)
            System.out.println("Date = " + s);
        Pattern pat = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
        Matcher m = pat.matcher(s);
        if (m.matches()) {
            yyyy = m.group(1);
            mm = m.group(2);
            dd = m.group(3);
        } else {
            System.out.println("bad date: " + s);
        }
        dcncDate = s;
    }

    private void parseFacility(String s) {
        if (verbose)
            System.out.println("Facility = " + s);
        dcncFacility = s;
    }

    private void parseStandard(String s) {
        if (verbose)
            System.out.println("Standard = " + s);
        dcncStandard = s;
    }

    private void parsePackage(String s) {
        if (verbose)
            System.out.println("Package = " + s);
        dcncPackage = s;
    }

    public DigitalCinemaName(boolean verbose) {
        this.verbose = verbose;
    }

    private Comment mSynthComment(String s) {
        return dom.createComment(" Next element for XML validation; not part of DCNC ");
    }

    private Element mAspectRatio() throws Exception {
        String s = null;
        if (dcncRatio == null) {
            switch(dcncAspect) {
            case "F":
                s = "185 100";
                dcncRatio = "185";
                break;
            case "S":
                s = "239 100";
                dcncRatio = "239";
                break;
            case "C":
                s = "190 100";
                dcncRatio = "190";
                break;
            default:
                s = "---invalid---";
                break;
            }
        } else {
            s = dcncRatio.substring(0, 1) + dcncRatio.substring(2) + " 100";
        }
        return mGenericElement("ScreenAspectRatio", s);
    }

    protected Element mMetadata() throws Exception {
        Element e = dom.createElement("meta:CompositionMetadataAsset");

        // Id
        e.appendChild(mGenericElement("Id", uuid()));

        // Release Territory
        Element e2 = mGenericElement("meta:ReleaseTerritory", dcncTerritory);
        e.appendChild(e2);

        // Version Number
        e.appendChild(mVersionNumber(contentVersion));

        // Chain
        e2 = mGenericElement("meta:Chain", optChain);
        e.appendChild(e2);

        // Distributor
        String longName = Studio.getLongName(dcncStudio);
        String s = String.format("%s (%s)", dcncStudio,
                                 (dcncFacility == null) ? "unknown" : longName);
        e2 = mGenericElement("meta:Distributor", s);
        e.appendChild(e2);

        // Luminance
        e2 = mGenericElement("meta:Luminance", optLuminance);
        Attr units = dom.createAttribute("units");
        units.setValue("foot-lambert");
        e2.setAttributeNode(units);
        e.appendChild(e2);

        e2 = mGenericElement("meta:MainSoundConfiguration", dcncAudio);
        e.appendChild(e2);

        // MainPictureStoredArea
        e2 = dom.createElement("meta:MainPictureStoredArea");
        Element e3 = dom.createElement("Width");
        Text tmp = dom.createTextNode(storeWidth.get(dcncAspect + dcncResolution).toString());
        e3.appendChild(tmp);
        e2.appendChild(e3);
        e3 = dom.createElement("Height");
        tmp = dom.createTextNode(storeHeight.get(dcncAspect + dcncResolution).toString());
        e3.appendChild(tmp);
        e2.appendChild(e3);
        e.appendChild(e2);

        // MainPictureActiveArea
        e2 = dom.createElement("meta:MainPictureActiveArea");
        e3 = dom.createElement("Width");
        double ar = Double.parseDouble(dcncRatio);
        int w = storeWidth.get(dcncAspect + dcncResolution);
        int h = storeHeight.get(dcncAspect + dcncResolution);
        w = (int) (h * ar);
        tmp = dom.createTextNode(String.format("%d", w));
        e3.appendChild(tmp);
        e2.appendChild(e3);
        e3 = dom.createElement("Height");
        tmp = dom.createTextNode(storeHeight.get(dcncAspect + dcncResolution).toString());
        e3.appendChild(tmp);
        e2.appendChild(e3);
        e.appendChild(e2);

        // Subtitles
        s = "";
        for (int i=1; i<lang.length; i++) {
            if (lang[i] != null) {
                s += lang[i];
                if (i != lang.length-1 && lang[i+1] != null)
                    s += " ";
            }
        }
        e2 = mGenericElement("meta:MainSubtitleLanguageList", s);
        e.appendChild(e2);

        return e;
    } /* mMetadata() */

    protected Element mMainPicture(String mpname) throws DOMException, Exception {
        // MainPicture or MainStereoscopic picture
        Element e = dom.createElement(mpname);
        e.appendChild(mSynthComment("Id"));
        e.appendChild(mGenericElement("Id", uuid()));
        e.appendChild(mSynthComment("EditRate"));
        e.appendChild(mGenericElement("EditRate", "1 1"));
        e.appendChild(mSynthComment("IntrinsicDuration"));
        e.appendChild(mGenericElement("IntrinsicDuration", "0"));
        if (optFrameRate == null) {
            e.appendChild(mSynthComment("FrameRate"));
            e.appendChild(mGenericElement("FrameRate", "1 1"));
        } else {
            e.appendChild(mGenericElement("FrameRate", optFrameRate + " 1"));
        }
        e.appendChild(mAspectRatio());
        return e;
    }

    protected Element mReel() throws Exception {
        Element e, e2, e3, e4;
        e = dom.createElement("ReelList");
        e2 = dom.createElement("Reel");
        e.appendChild(e2);

        // Id
        e2.appendChild(mGenericElement("Id", uuid()));
        e3 = dom.createElement("AssetList");
        e2.appendChild(e3);

        if (!opt3D)
            e3.appendChild(mMainPicture("MainPicture"));

        // MainSound
        e4 = dom.createElement("MainSound");
        e4.appendChild(mSynthComment("Id"));
        e4.appendChild(mGenericElement("Id", uuid()));
        e4.appendChild(mSynthComment("EditRate"));
        e4.appendChild(mGenericElement("EditRate", "1 1"));
        e4.appendChild(mSynthComment("IntrinsicDuration"));
        e4.appendChild(mGenericElement("IntrinsicDuration", "0"));
        e4.appendChild(mGenericElement("Language", lang[0]));
        e3.appendChild(e4);

        if (!(lang[1] == null || lang[1].equals("xx"))) {
            e4 = dom.createElement("MainSubtitle");
            e4.appendChild(mSynthComment("Id"));
            e4.appendChild(mGenericElement("Id", uuid()));
            e4.appendChild(mSynthComment("EditRate"));
            e4.appendChild(mGenericElement("EditRate", "1 1"));
            e4.appendChild(mSynthComment("IntrinsicDuration"));
            e4.appendChild(mGenericElement("IntrinsicDuration", "0"));
            e4.appendChild(mGenericElement("Language", lang[1]));
            e3.appendChild(e4);
        }

        if (opt3D)
            e3.appendChild(mMainPicture("msp:MainStereoscopicPicture"));

        e3.appendChild(mMetadata());

        return e;
    } /* mReel */

    protected Element mContentVersion() throws Exception {
        Element e = dom.createElement("ContentVersion");
        e.appendChild(mGenericElement("Id", dcncPackage));
        e.appendChild(mGenericElement("LabelText", "Package Type"));

        return e;
    }

    protected Element mVersionNumber(String v) throws Exception {
        Element e = dom.createElement("VersionNumber");
        Text tmp = dom.createTextNode(v);
        String stat = (optPre) ? "pre" : ((optTemp) ? "temp" : "final");
        Attr status = dom.createAttribute("status");
        status.setValue(stat);
        e.setAttributeNode(status);
        e.appendChild(tmp);

        return e;
    }

    protected Element mIssuer() throws Exception {
        String longName = Facility.getLongName(dcncFacility);
        String s = String.format("%s (%s)", dcncFacility,
                                 (dcncFacility == null) ? "unknown" : longName);
        return mGenericElement("Issuer", s);                           
    }


    protected String uuid() {
        return "urn:uuid:" + UUID.randomUUID().toString();
    }

    protected Element mGenericElement(String name, String val) throws Exception {
        Element tia = dom.createElement(name);
        if (!val.equals("")) {
            Text tmp = dom.createTextNode(val);
            tia.appendChild(tmp);
        }
        return tia;
    } 

    private static final String cplns  = "http://www.smpte-ra.org/schemas/429-7/2006/CPL";
    private static final String metans = "http://www.smpte-ra.org/schemas/429-16/2014/CPL-Metadata";
    private static final String mspns  = "http://www.smpte-ra.org/schemas/429-10/2008/Main-Stereo-Picture-CPL";

    private Document makeXML() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Element e;

        dbf.setValidating(true);
        try {
            //get an instance of builder
            DocumentBuilder db = dbf.newDocumentBuilder();

            //create an instance of DOM
            dom = db.newDocument();
            root = dom.createElement("CompositionPlaylist");
            // Make it the root element of this new document
            Attr xmlns = dom.createAttribute("xmlns");
            // xmlns.setValue("http://www.smpte-ra.org/schemas/429-7/2006/CPL");
            xmlns.setValue(cplns);
            root.setAttributeNode(xmlns);

            xmlns = dom.createAttribute("xmlns:ds");
            xmlns.setValue("http://www.w3.org/2000/09/xmldsig#");
            root.setAttributeNode(xmlns);

            xmlns = dom.createAttribute("xmlns:xsi");
            xmlns.setValue("http://www.w3.org/2001/XMLSchema-instance");
            root.setAttributeNode(xmlns);

            xmlns = dom.createAttribute("xmlns:meta");
            xmlns.setValue(metans);
            root.setAttributeNode(xmlns);

            xmlns = dom.createAttribute("xmlns:msp");
            xmlns.setValue(mspns);
            root.setAttributeNode(xmlns);

            xmlns = dom.createAttribute("xsi:schemaLocation");
            xmlns.setValue(cplns + " " + "./CPL-S429-7-2006.xsd " + metans + " ./CPL-S429-16-2014.xsd " + 
                           mspns + " ./CPL-S429-10-2008.xsd");
            root.setAttributeNode(xmlns);

            dom.appendChild(root);

            // Id
            root.appendChild(mGenericElement("Id", uuid()));

            // AnnotationText
            root.appendChild(mGenericElement("AnnotationText", "Original DCNC: " + dcncOriginal));

            // IssueDate
            root.appendChild(mGenericElement("IssueDate", yyyy + "-" + mm + "-" + dd + "T00:00:00"));

            // Issuer
            root.appendChild(mIssuer());

            // Creator
            root.appendChild(mGenericElement("Creator", "DCNC Utility v0.2 by MovieLabs"));

            // ContentTitleText
            root.appendChild(mGenericElement("ContentTitleText", dcncTitle));

            // ContentKind
            root.appendChild(mGenericElement("ContentKind", contentKind));

            // ContentVersion
            root.appendChild(mContentVersion());

            // Rating
            root.appendChild(mRating());

            // Reel
            root.appendChild(mReel());

        } catch(ParserConfigurationException pce) {
            //dump it
            System.out.println("Error while trying to instantiate DocumentBuilder " + pce);
            System.exit(1);
        }

        return dom;
    }

    public void makeXML(String xmlFile) throws Exception {
       try {
            Document dom = makeXML();
            
            // Use a Transformer for output
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            
            DOMSource source = new DOMSource(dom);
            StreamResult result = new StreamResult(new FileOutputStream(xmlFile));
            transformer.transform(source, result);

        } catch (TransformerConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void parse(String s) throws XPathExpressionException {
        dcncOriginal = s;

        Pattern pat = Pattern.compile("^([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_" + 
                                      "([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_" + 
                                      "([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_([A-Za-z0-9-]+)_([A-Za-z0-9-]+)$");
        Pattern contentType = Pattern.compile("^(FTR|TLR|TSR|PRO|TST|RTG-F|RTG-T|SHR|ADV|XSN|PSA|POL)(.*)$");

        Matcher m = pat.matcher(s);
        if (m.matches()) {
            System.out.println("matched");

            // Title
            parseTitle(m.group(1));

            // Content Type
            parseContentType(m.group(2));

            // Aspect
            parseAspect(m.group(3));

            // Language
            parseLanguage(m.group(4));

            // Territory & Rating
            parseTerritory(m.group(5));

            // Audio
            parseAudio(m.group(6));

            // Resolution
            parseResolution(m.group(7));

            // Studio
            parseStudio(m.group(8));
 
            // Date
            parseDate(m.group(9));

            // Facility
            parseFacility(m.group(10));

            // Standard
            parseStandard(m.group(11));

            // Package
            parsePackage(m.group(12));

            // for (int i=1; i<=m.groupCount(); i++) {
            //     System.out.println(field[i-1] + ": " + m.group(i));
            // }
        } else {
            System.out.println("no match");
        }
    }
}
