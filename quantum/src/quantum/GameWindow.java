package quantum;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

import javafx.embed.swing.JFXPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

@SuppressWarnings("serial")
class LevelComponent extends JPanel {
	
	public boolean isFocusable() {return true;}
	private UpdateTask updatetask;
	private Thread updatethread;
	int width,height;
	float scale;
	LevelManager manager;
	Font font;
	public LevelComponent(String basename,String filename, int width, int height, Font font)
	{
		this.width = width;
		this.height = height;
		this.font = font;
		manager = new LevelManager(basename,filename,this);
		scale = (float)height/manager.getGameRender().height();
		float xscale = (float)width/manager.getGameRender().width();
		assert(Math.abs(1-scale/xscale)<0.01);
	}
	private void initbuttons()
	{
		setLayout(new BorderLayout());
		JPanel footer = new JPanel();
		add(footer,BorderLayout.PAGE_END);
		footer.setFocusable(false);
		footer.setOpaque(false);
		if (!manager.isReward())
		{
			if (manager.isSplash())
			{
				footer.setLayout(new FlowLayout(FlowLayout.CENTER));
				JButton nextbutton = new JButton("Press SPACE to continue");
				nextbutton.setForeground(Color.BLACK);
				nextbutton.setFont(font.deriveFont(18f));
				nextbutton.setFocusable(false);
				nextbutton.setOpaque(false);
				nextbutton.setActionCommand("NEXTSPLASH");
				nextbutton.addActionListener(manager.getControlState());
				nextbutton.setCursor(new Cursor(Cursor.HAND_CURSOR));
				nextbutton.setFocusPainted(false);
				nextbutton.setBorderPainted(false);
				nextbutton.setContentAreaFilled(false);
				footer.add(nextbutton);
			}
			else 
			{
				footer.setLayout(new FlowLayout(FlowLayout.RIGHT));
				JButton restartbutton = new JButton("<html><u>R</u>estart level</html>");
				restartbutton.setActionCommand("RESET");
				restartbutton.addActionListener(manager.getControlState());
				restartbutton.setFocusable(false);
				restartbutton.setCursor(new Cursor(Cursor.HAND_CURSOR));
			    footer.add(restartbutton);
			    JButton menubutton = new JButton("<html><u>M</u>enu</html>");
			    menubutton.setActionCommand("MENU");
			    menubutton.addActionListener(manager.getControlState());
			    menubutton.setFocusable(false);
			    menubutton.setCursor(new Cursor(Cursor.HAND_CURSOR));
			    footer.add(menubutton);
			}
		}
		final String htmlheader="<html><center>";
		final String htmlfooter="</center></html>";
		final String labelcontents = htmlheader+manager.getText()+htmlfooter;
		//System.out.println(labelcontents);
		JLabel text = new JLabel(labelcontents);
		if (!manager.isReward())
			text.setForeground(Color.BLACK);
		else
			text.setForeground(Color.WHITE);
		text.setFont(font);
		text.setFocusable(false);
		text.setOpaque(false);
		text.setHorizontalAlignment(SwingConstants.CENTER);
		add(text,BorderLayout.CENTER);
	}
	public void initSwing()
	{
		setDoubleBuffered(true); 
		setFocusTraversalKeysEnabled(false);
	    requestFocusInWindow();
	    initbuttons();
	    updatetask = new UpdateTask(manager);
	    updatethread = new Thread(updatetask);
	    updatethread.start();
	    addKeyListener(manager.getControlState());
	}
	public void waitUntilLevelFinished() throws InterruptedException, InvocationTargetException
	{
		//PotentialGridGenerator.drawPotential(manager.qd, "grid_dump", "png");
		updatethread.join();
		SwingUtilities.invokeAndWait(new Runnable() {public void run() {
			removeKeyListener(manager.getControlState());
		}});
	}
	public int width() {return width;}
	public int height() {return height;}
	@Override
	public Dimension getPreferredSize() {return new Dimension(width(),height());}
	
	private final RenderingHints rh = new RenderingHints(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	public void paintComponent(Graphics g) {
			super.paintComponent(g);
			((Graphics2D)g).setRenderingHints(rh);
			GameRender gr = manager.getGameRender();
			BufferedImage background = manager.getBackground();
			if (background!=null)
				g.drawImage(background, 0,0,width(),height(),0,0,background.getWidth(),background.getHeight(),null);
			g.drawImage(gr.getImage(), 0,0,width(),height(),0,0,gr.width(),gr.height(),null);
			g.setFont(g.getFont().deriveFont(Font.BOLD));
			if (!manager.goalIsSounding())
				for (QCounter c : manager.getCounters())
				{
					final Dimension pos = c.getTextLocation();
					g.setColor(c.getColour());
					final String[] lines = c.getText().split("\n");
					assert(lines.length<=2 && lines.length>0);
					short linenum = 0;
					for (String line : lines)
					{
						final Rectangle2D stringbounds = g.getFontMetrics().getStringBounds(line, g);
						final int xoffset = (int) -stringbounds.getWidth()/2;
						final float lineoffset = (lines.length==1) ? 0.5f : linenum;
						final int yoffset = (int) (stringbounds.getHeight() * lineoffset);
						g.drawString(line,(int)(pos.width*scale)+xoffset,
										  (int)(pos.height*scale)+yoffset);
						linenum++;
					}
				}
	}
}
class UpdateTask implements Runnable
{
	private LevelManager manager;
	public UpdateTask(LevelManager manager)
	{
		this.manager = manager;
	}
	private static final boolean benchmark = true;

	public void run()
	{
		final long gfxframetime = 33000000; //30 fps
		final long quantum_frames_per_gfx_frame = gfxframetime / manager.quantumFrameTimeNanos();
		int iterations = 0;
		final long starttime = System.nanoTime();
		long lastframetime = starttime;
		long quantumframes_this_frame = 0;
		manager.startSoundtrack();
		while (!manager.goalSoundedIfNecessary())
		{
			iterations++;
			quantumframes_this_frame++;
			if (benchmark && iterations==1000)
			{
				final long endtime = System.currentTimeMillis();
				final float time = (float)(endtime-starttime)/1000000000.f;
				//System.out.println(time);
			}
			//mechanism to update quantum sim and graphics only when needed
			//the time checks don't add significant overhead, i checked
			if (quantumframes_this_frame < quantum_frames_per_gfx_frame)
				manager.getQD().step();
			else
			{
				long timesincelastframe = System.nanoTime()-lastframetime;
				long sleeptime = gfxframetime - timesincelastframe;
				if (sleeptime>0)
				{
					try { Thread.sleep(sleeptime/1000000); } catch (InterruptedException e) {}
				}
			}
			final long currenttime = System.nanoTime();
			if (currenttime-lastframetime > gfxframetime) //30fps
			{
				quantumframes_this_frame = 0;
				lastframetime = currenttime;
				manager.updateGraphics();
			}
			if (manager.shouldTerminate())
				manager.soundGoalIfNecessaryAsync();
		}
		manager.stopSoundtrack();
	}
}

@SuppressWarnings("serial")
public class GameWindow extends JPanel  {
		GameManager gm;
		public void init()
		{
			gm = new GameManager(this,"http://fiftysevendegreesofrad.github.io/quantum/levels/");
			
			//initialize javafx - just for mp3 playback
			@SuppressWarnings("unused")
			final JFXPanel fxPanel = new JFXPanel();
			
			Thread t = new Thread(new Runnable() {
				public void run() { try {
					gm.play();
				} catch (Exception e) {
					e.printStackTrace();
				} }
			});
			t.start();
		}
		public Dimension getPreferredSize() {return new Dimension(700,525);}
		public String getTextReport() { return gm.getTextReport();}
		
		public static void main(String[] args) {
			  JFrame f = new JFrame();
			  f.setTitle("Quantum Marble Maze");
			  f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			  GameWindow g = new GameWindow();
			  g.setBackground(Color.BLACK);
//			  //uncomment to demonstrate padding problem
//			  Border border = BorderFactory.createLineBorder(Color.BLACK);
//	          g.setBorder(border);
			  f.add(g);
			  g.init();
			  f.pack();
			  Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
			  f.setLocation(dim.width/2-f.getSize().width/2, dim.height/2-f.getSize().height/2);
			  f.setResizable(false);
			  f.setVisible(true);
		}
}

