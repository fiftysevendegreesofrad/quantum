package quantum;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class LevelList {
	String codebase;
	long creationtime;
	ArrayList<LevelScene> list;
	ArrayList<Integer> bookmarks;
	ArrayList<Boolean> completedBookmarks;
	Scene ending, menuscene;
	public LevelList(String codebase,Scene menuscene)
	{
		this.menuscene = menuscene;
		creationtime = System.currentTimeMillis();
		this.codebase = codebase;
		list = new ArrayList<LevelScene>();
		bookmarks = new ArrayList<Integer>();
		completedBookmarks = new ArrayList<Boolean>();
		
		Document dom;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(codebase+"levels.xml");

            Element root = dom.getDocumentElement();
            this.ending = new LevelScene(codebase,root.getAttribute("reward"),this,-1,menuscene);
            NodeList nodes = root.getChildNodes();
            
            for(int i=0; i<nodes.getLength(); i++){
            	Node node = nodes.item(i);
                switch(node.getNodeName())
                {
                case "level":
                	NodeList levelnodes = node.getChildNodes();
                	bookmarks.add(list.size());
                	completedBookmarks.add(false);
                	boolean had_challenge = false;
                	for (int j=0;j<levelnodes.getLength();j++)
                	{
                		node = levelnodes.item(j);
                		switch(node.getNodeName())
                		{
                		//splash and challenge are the same except splash has no goals
                		//and we enforce only one challenge per level
                		case "challenge":
                			if (had_challenge)
                			{
                				System.out.println("already had challenge");
                				System.exit(1);
                			}
                			had_challenge = true;
                			//deliberate fall through to:
                		case "splash":
                			list.add(new LevelScene(codebase,node.getTextContent(),
            	            		this,list.size(),menuscene));
                			break;
                		default:
                			break;
                		}
                	}
                }
            }
        } catch (ParserConfigurationException pce) {
            System.out.println(pce.getMessage());
        } catch (SAXException se) {
            System.out.println(se.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
	}
	public void markComplete(int level)
	{
		//mark last bookmark before this level complete
		int j;
		for (j=0;j<bookmarks.size();j++)
			if (bookmarks.get(j)>level)
				break;
		j--;
		if (j>=0)
			completedBookmarks.set(j, true);
	}
	public Scene getNext(int i)
	{
		//return next scene
		boolean allcomplete = true;
		for (boolean completed : completedBookmarks)
			allcomplete &= completed;
		if (allcomplete)
			return ending;
		else
		{
			if (i<list.size()-1)
				return list.get(i+1);
			else
				return menuscene;
		}		
	}
	public Scene getFirst()
	{
		return list.get(0);
	}
	public Scene getbookmark(int i)
	{
		return list.get(bookmarks.get(i));
	}
	public boolean getBookmarkCompletedState(int i)
	{
		return completedBookmarks.get(i);
	}
	public String getTextReport() {
		int completedcount = 0;
		int total = 0;
		for (boolean completed : completedBookmarks)
		{
			total++;
			if (completed) completedcount++;
		}
		long time = (System.currentTimeMillis()-creationtime)/1000;
		long minutes = time/60;
		long seconds = time%60;
		return String.format("I completed %d/%d levels of Quantum Marble Maze in %d minutes %02d seconds!  ",completedcount,total,minutes,seconds);
	}
}
