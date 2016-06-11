package quantum;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

@SuppressWarnings("serial")
public class MenuComponent extends JPanel implements ActionListener {
	LevelList levellist;
	int width,height;
	Container container;
	Integer bookmarkchosen;
	Object waitlock;
	Image bg;
	public MenuComponent(LevelList levellist,int width,int height,Container container)
	{
		this.levellist = levellist;
		this.width = width;
		this.height = height;
		this.container = container;
		this.waitlock = new Object();
		this.bookmarkchosen = -1;
		try {
			bg = ImageIO.read(getClass().getResource("images/menubg.jpg"));
		} catch (IOException e) {}
	}
	public Dimension getPreferredSize() {return new Dimension(width,height);}
	void initSwing() //todo only give container to initswing method
	{
		BoxLayout boxla = new BoxLayout(this,BoxLayout.Y_AXIS);
		setLayout(boxla);
		JPanel footer = new JPanel();
		footer.setFocusable(false);
		footer.setOpaque(false);
		footer.setLayout(new FlowLayout(FlowLayout.LEFT));
		for (int i=0; i<levellist.bookmarks.size(); i++)
		{
			JButton b1 = new JButton(String.format("%02d", i));
			b1.setMargin(new Insets(0,5,0,5));
			b1.setMaximumSize(new Dimension(10,10));
			if (levellist.getBookmarkCompletedState(i))
				b1.setBackground(Color.GREEN);
			b1.setCursor(new Cursor(Cursor.HAND_CURSOR));
			b1.setActionCommand(new Integer(i).toString());
			b1.addActionListener(this);
		    b1.setHorizontalTextPosition(SwingConstants.CENTER);
			footer.add(b1);
		}
		add(Box.createVerticalStrut(490));
		add(footer);
	}
	@Override
	public void actionPerformed(ActionEvent e)
	{
		bookmarkchosen = Integer.parseInt(e.getActionCommand());
		synchronized(waitlock)
		{
			waitlock.notify();
		}
	}
	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		g.drawImage(bg,0,0,null);
	}
	Scene waitAndReturnSelection()
	{
		synchronized (waitlock)
		{
			while (bookmarkchosen==-1)
				try {
					waitlock.wait();
				} catch (InterruptedException e) {}
		}
		return levellist.getbookmark(bookmarkchosen);
	}
}
