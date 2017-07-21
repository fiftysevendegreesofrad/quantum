package quantum;

import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.xml.parsers.*;

import org.xml.sax.*;
import org.w3c.dom.*;

public class LevelLoader
{
	public static boolean load(String baseurl,String levelfile,LevelManager lm)
	{
		Document dom;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(baseurl+levelfile);

            Element root = dom.getDocumentElement();
            lm.init(Float.parseFloat(root.getAttribute("scale")),
            		Float.parseFloat(root.getAttribute("dt")),
            		Float.parseFloat(root.getAttribute("maxtilt")),
            		root.getAttribute("colourmap"),
            		Float.parseFloat(root.getAttribute("qft"))/1.5f);

            NodeList nodes = root.getChildNodes();
            
            //get mask first
            for(int i=0; i<nodes.getLength(); i++)
                if (nodes.item(i).getNodeName()=="mask")
                	lm.setMask(ImageIO.read(new URL(baseurl+nodes.item(i).getTextContent())));
                
            for(int i=0; i<nodes.getLength(); i++){
              Node node = nodes.item(i);
              Element e;
              switch (node.getNodeName())
              {
              case "synth":
            	  lm.setSynth(Float.parseFloat(((Element)node).getAttribute("pitchdown")));
            	  break;
              case "audioloop":
            	  e = (Element)node;
            	  lm.setLoop(baseurl+node.getTextContent());
            	  if (e.hasAttribute("start")) 
            		  lm.setLoopStart(Double.parseDouble(e.getAttribute("start")));
            	  if (e.hasAttribute("length")) 
            		  lm.setLoopLength(Double.parseDouble(e.getAttribute("length")));
            	  break;
              case "background":
            	  //System.out.println("setting bg "+node.getTextContent());
            	  lm.setBackground(ImageIO.read(new URL(baseurl+node.getTextContent())));
            	  break;
              case "text":
            	  lm.addText(node.getTextContent());
            	  break;
              case "potentialplane":
            	  e = (Element)node;
            	  lm.addPotentialPlane(Float.parseFloat(e.getAttribute("tl")),
            			  Float.parseFloat(e.getAttribute("tr")),
            			  Float.parseFloat(e.getAttribute("bl")),
            			  Float.parseFloat(e.getAttribute("br")),
            			  e.getAttribute("mask"));
            	  break;
              case "potentialwell":
            	  e = (Element)node;
            	  lm.addPotentialWell(Integer.parseInt(e.getAttribute("x")),
            			  Integer.parseInt(e.getAttribute("y")),
            			  Float.parseFloat(e.getAttribute("surfacer")),
            			  Float.parseFloat(e.getAttribute("depth")));
            	  break;
              case "potentialcone":
            	  e = (Element)node;
            	  lm.addPotentialCone(Integer.parseInt(e.getAttribute("x")),
            			  Integer.parseInt(e.getAttribute("y")),
            			  Float.parseFloat(e.getAttribute("r")),
            			  Float.parseFloat(e.getAttribute("depth")));
            	  break;
              case "gaussian":
            	  e = (Element)node;
            	  lm.addGaussian(Integer.parseInt(e.getAttribute("x")),
            			  Integer.parseInt(e.getAttribute("y")),
            			  Float.parseFloat(e.getAttribute("sigma")),
            			  Float.parseFloat(e.getAttribute("px")),
            			  Float.parseFloat(e.getAttribute("py")),
            			  Float.parseFloat(e.getAttribute("a")));
            	  break;
              case "delta":
            	  e = (Element)node;
            	  lm.addDelta(Integer.parseInt(e.getAttribute("x")),
            			  Integer.parseInt(e.getAttribute("y")),
            			  Float.parseFloat(e.getAttribute("a")));
            	  break;
              case "goal":
            	  e = (Element)node;
            	  lm.addGoal(e.getAttribute("mask"),
            			  Integer.parseInt(e.getAttribute("target")));
            	  break;
              case "reward":
            	  lm.setReward();
            	  break;
              case "walls":
            	  e = (Element)node;
            	  lm.setWalls(e.getAttribute("mask"));
            	  break;
              case "sink":
            	  e = (Element)node;
            	  lm.setSink(e.getAttribute("mask"));
            	  break;
              case "collapse":
            	  e = (Element)node;
            	  lm.addCollapse(e.getAttribute("mask"),
            			  Integer.parseInt(e.getAttribute("target")),
		            	  Float.parseFloat(e.getAttribute("sigma")));
            	  break;
              case "trap":
            	  e = (Element)node;
            	  lm.addTrap(e.getAttribute("mask"),
            			  Integer.parseInt(e.getAttribute("target")));
            	  break;
              case "steepeningvalley":
            	  e = (Element)node;
            	  lm.addSteepeningValley();
            	  break;
              }
            }
            lm.finishLoad();
            return true;
            
        } catch (ParserConfigurationException pce) {
            System.out.println(pce.getMessage());
        } catch (SAXException se) {
            System.out.println(se.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        } catch (ClassCastException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        return false;
	}
}

