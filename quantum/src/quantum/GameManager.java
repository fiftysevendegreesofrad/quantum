package quantum;

import java.awt.Container;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;

import javax.swing.JComponent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

interface Scene {
	Scene play(RootPaneContainer frame, Font mainFont) throws InterruptedException, InvocationTargetException;
}

class MenuScene implements Scene
{
	LevelList levellist;
	public void configure(LevelList levellist)
	{
		this.levellist = levellist;
	}
	public Scene play(RootPaneContainer frame,Font font) throws InvocationTargetException, InterruptedException
	{
		final Container fframe = (JComponent)(frame.getContentPane());
		final MenuComponent menu = new MenuComponent(levellist,700,525,fframe); //todo these dimensions appear too oft
		SwingUtilities.invokeAndWait(new Runnable() {public void run() {
			fframe.add(menu);
			menu.initSwing();
			fframe.revalidate();
			fframe.repaint();
		}});
		
		Scene retval = menu.waitAndReturnSelection(); 
		
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {fframe.remove(menu);}
		});
		return retval;
	}
}

class LevelScene implements Scene
{
	String codebase,filename;
	int levelIndex; 
	private Scene menuscene;
	LevelList levellist;
	LevelScene (String codebase,String filename,LevelList levellist,int levelIndex,Scene menuscene)
	{
		this.codebase = codebase;
		this.filename = filename;
		this.levellist = levellist;
		this.levelIndex = levelIndex;
		this.menuscene = menuscene;
	}
	public Scene play(RootPaneContainer frame,Font font) throws InterruptedException, InvocationTargetException
	{
		final Container fframe = (JComponent)(frame.getContentPane());
		final LevelComponent level = new LevelComponent(codebase,filename,700,525,font);
		SwingUtilities.invokeAndWait(new Runnable() {public void run() {
			fframe.add(level);
			level.initSwing();
			fframe.revalidate();
			fframe.repaint();
		}});
		
		level.waitUntilLevelFinished(); 
		
		SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {fframe.remove(level);}
		});
		
		if (level.manager.menuRequested()) 
			return menuscene;
		else
		{
			assert (levelIndex!=-1);
			if (!level.manager.isSplash())
			{
				levellist.markComplete(levelIndex);
			}
			return levellist.getNext(levelIndex);
		}
	}
}

public class GameManager {
	String codebase;
	RootPaneContainer frame;
	Scene nextScene;
	Font mainFont;
	LevelList levellist;

	public GameManager(RootPaneContainer frame,String codebase)
	{
		this.frame = frame;
		
		InputStream is = getClass().getResourceAsStream("fonts/Exo-Bold-renamed.ttf"); 
		try {
			mainFont = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(24f);
		} catch (FontFormatException | IOException e) {
			System.out.println("falling back to default font");
			mainFont = new Font("SansSerif",Font.BOLD,24);
		}
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(mainFont);
		
		MenuScene menuscene = new MenuScene();
		levellist = new LevelList(codebase,menuscene); 
		menuscene.configure(levellist);
		nextScene = levellist.getFirst();
	}
	
	public void play() throws InvocationTargetException, InterruptedException
	{
		while (true)
		{
			nextScene = nextScene.play(frame,mainFont);
		}
	}

	public String getTextReport() {
		return levellist.getTextReport();
	}
}
