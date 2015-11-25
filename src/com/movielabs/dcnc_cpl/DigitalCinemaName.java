package com.movielabs.dcnc_cpl;

import java.io.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.*;

public class DigitalCinemaName {
    private boolean verbose = false;
    private Document dom = null;
    private Element root;

    private String dcncOriginal, dcncTitle, dcncContentType, dcncAspect, dcncRatio,
        dcncLanguage, dcncTerritory, dcncRating, dcncAudio, dcncResolution,
        dcncStudio, dcncDate, dcncFacility, dcncStandard, dcncPackage;
    private static final String[] ISO3166 = Locale.getISOCountries();

    private String[] lang = new String[3];
    private String yyyy, mm, dd;
    private String contentKind;
    private String contentVersion = "1";
    private boolean badISO3166 = false;
    private boolean optTemp, optPre, optRedBand, opt3D;
    private String optLuminance, optFrameRate, optChain;

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
                            optLuminance = m.group(1);
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

    private void parseTerritory(String s) {
        String[] tr = s.split("-");
        dcncTerritory = tr[0];
        badISO3166 = Arrays.binarySearch(ISO3166, dcncTerritory) == -1;
        dcncRating = (tr.length == 2) ? tr[1] : null;
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
            if (dcncTerritory.equals("US"))
                e2.appendChild(mGenericElement("Agency", "http://www.mpaa.org/2003-ratings"));
            else
                e2.appendChild(mGenericElement("Agency", 
                                  "unknown territory: " + dcncTerritory + ")"));
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
        dcncResolution = s;
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
                break;
            case "S":
                s = "239 100";
                break;
            case "C":
                s = "190 100";
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

        // Chain
        e2 = mGenericElement("meta:Chain", optChain);
        e.appendChild(e2);

        // Luminance
        e2 = mGenericElement("meta:Luminance", optLuminance);
        Attr units = dom.createAttribute("units");
        units.setValue("foot-lambert");
        e2.setAttributeNode(units);
        e.appendChild(e2);

        e2 = mGenericElement("meta:MainSoundConfiguration", dcncAudio);
        e.appendChild(e2);

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

        // MainPicture
        e4 = dom.createElement("MainPicture");
        e4.appendChild(mSynthComment("Id"));
        e4.appendChild(mGenericElement("Id", uuid()));
        e4.appendChild(mSynthComment("EditRate"));
        e4.appendChild(mGenericElement("EditRate", "1 1"));
        e4.appendChild(mSynthComment("IntrinsicDuration"));
        e4.appendChild(mGenericElement("IntrinsicDuration", "0"));
        if (optFrameRate == null) {
            e4.appendChild(mSynthComment("FrameRate"));
            e4.appendChild(mGenericElement("FrameRate", "1 1"));
        } else {
            e4.appendChild(mGenericElement("FrameRate", optFrameRate + " 1"));
        }
        e4.appendChild(mAspectRatio());
        e3.appendChild(e4);

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

        e3.appendChild(mMetadata());

        return e;
    } /* mReel */

    protected Element mContentVersion() throws Exception {
        Element e = dom.createElement("ContentVersion");
        e.appendChild(mGenericElement("Id", contentVersion));
        e.appendChild(mGenericElement("LabelText", "dervied from DCNC version"));

        return e;
    }

    protected Element mIssuer() throws Exception {
        String longName = Facility.getLongName(dcncFacility);
        String s = String.format("%3s (%s)", dcncFacility,
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

    private static final String cplns = "http://www.smpte-ra.org/schemas/429-7/2006/CPL";
    private static final String metans = "http://www.smpte-ra.org/schemas/429-16/2014/CPL-Metadata";

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
            xmlns.setValue("http://www.smpte-ra.org/schemas/429-16/2014/CPL-Metadata");
            root.setAttributeNode(xmlns);

            xmlns = dom.createAttribute("xsi:schemaLocation");
            xmlns.setValue(cplns + " " + "./CPL-S429-7-2006.xsd " + metans + "./CPL-S429-16-2014.xsd");
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
            root.appendChild(mGenericElement("Creator", "dcnc Utility v0.1 by MovieLabs"));

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

    public void parse(String s) {
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
