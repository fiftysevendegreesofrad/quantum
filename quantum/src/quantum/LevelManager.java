package quantum;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

class LevelManager
{
	LevelComponent lc;
	QuantumData qd;
	GameRender gr;
	ControlState controlstate;
	Soundtrack soundtrack;
	BufferedImage mask, background;
	ArrayList<QCounter> counters;
	private boolean allGoalsSatisfiedThisRound, allGoalsSatisfiedThreadSafe = false;
	Set<Integer> colourset;
	private float scale;
	String text;
	private boolean isReward;
	void setReward() {this.isReward=true;}
	boolean isReward() {return isReward;}
	Clip goalSoundingClip = null;
	private boolean isSplash;
	private boolean goalsoundedifnecessary = false;
	private long quantumframetimenanos;
	LevelManager(String basename,String filename,LevelComponent lc)
	{
		text = "";
		this.isReward=false;
		this.lc = lc;
		this.controlstate = new ControlState();
		LevelLoader.load(basename, filename, this);
		if (isSplash())
			controlstate.disableCursors();
	}
	void init(float scale,float dt,float maxtilt,String colourmap,float thousanditertimesecs)
	{
		this.scale = scale;
		qd = new QuantumData((int)(lc.width()/scale),(int)(lc.height()/scale),controlstate);
		qd.setDeltaT(dt);
		qd.setMaxTilt(maxtilt);
		gr = new GameRender(qd);
		setColorMap(colourmap);
		counters = new ArrayList<QCounter>();
		soundtrack = new Silence();
		quantumframetimenanos = (long) (thousanditertimesecs/1000*1000000000);
	}
	void finishLoad()
	{
		isSplash = (counters.size()==0);
	}
	void addGaussian(int x,int y,float sigma,float px,float py,float a)
	{
		qd.addGaussian((int)(x/scale), (int)(y/scale), sigma, (int)(px/scale), (int)(py/scale), a);
	}
	void addGaussianQUnits(int x,int y,float sigma,float px,float py,float a)
	{
		qd.addGaussian(x,y, sigma, px,py, a);
	}
	void setBackground(BufferedImage bufferedImage)
	{
		background = bufferedImage;
	}
	void setMask(BufferedImage bufferedImage)
	{
		mask = bufferedImage;
		colourset = new HashSet<Integer>();
		for (int x=0;x<mask.getWidth();x++)
			for (int y=0;y<mask.getHeight();y++)
				colourset.add(mask.getRGB(x,y) & 0xffffff);
	}
	void addDelta(int x,int y,float a) {qd.addDelta((int)(x/scale),(int)(y/scale),a);}
	void setColorMap(String cmap) {
		switch(cmap.toLowerCase())
		{
		case "amp":
			gr.setAmpColourMap();
			break;
		case "rainbow":
			gr.setRainbowColourMap();
			break;
		default:
			System.out.println("unrecognised color map");
		}
	}
	public void addPotentialPlane(float tl, float tr, float bl, float br, String mask) 
	{
		BoolArray2d m = getSubMask(mask);
		//find extremes
		int top = m.height(), left = m.width(), bottom=0, right=0; 
		for (int x=0;x<m.width();x++)
			for (int y=0;y<m.height();y++)
				if (m.get(x,y))
				{
					if (x<left) left=x;
					if (x>right) right=x;
					if (y<top) top=y;
					if (y>bottom) bottom=y;
				}
		//add potential
		float potwidth = right-left;
		if (potwidth==0) potwidth=1;
		float potheight = bottom-top;
		if (potheight==0) potheight=1;
		for (int x=left;x<=right;x++)
			for (int y=top;y<=bottom;y++)
				if (m.get(x,y))
				{
					float rx = (float)(x-left)/potwidth;
					float ry = (float)(y-top)/potheight;
					//potx0, potxh = potential at x,0 and x,height
					float potx0 = tl+(tr-tl)*rx;
					float potxh = bl+(br-bl)*rx;
					float p = potx0+(potxh-potx0)*ry;
					qd.addPotential(x,y,p);
				}
	}
	public void addPotentialWell(int xc, int yc, float R,
			float corepot) {
		xc = (int) (xc/scale);
		yc = (int) (yc/scale);
		R = R/scale;
		//above surface potential = -a/r
		//below surface potential = b(r^2-3R^2)
		//so solve to find a and b for r==R
		//=> a = -s/R, b=-s/2R^2
		//what about corepot?
		//b = -cp/3R^2
		//then at surface
		//a = 2bR^3
		assert(corepot<0);
		final float b = -corepot/3/R/R; //-surfacepot/R;
		final float a = 2*b*R*R; //-surfacepot/2/R/R;
		for (int x=0;x<qd.width;x++)
			for (int y=0;y<qd.height;y++)
			{
				final float dx = x-xc;
				final float dy = y-yc;
				final float r = (float) Math.pow(dx*dx+dy*dy, 0.5);
				if (r<R)
					qd.addPotential(x, y, b*(r*r-3*R*R)); 
				else
					qd.addPotential(x, y, -a/r); 
			}
		
	}
	public void addPotentialCone(int xc, int yc, float R,
			float depth)
	{
		xc = (int) (xc/scale);
		yc = (int) (yc/scale);
		R = R/scale;
		for (int x=0;x<qd.width;x++)
			for (int y=0;y<qd.height;y++)
			{
				final float dx = x-xc;
				final float dy = y-yc;
				final float r = (float) Math.pow(dx*dx+dy*dy, 0.5);
				if (r<R)
					qd.addPotential(x, y, (1.f-r/R)*depth);
			}
	}
	public void setWalls(String mask) {
		qd.setWalls(getSubMask(mask));
	}
	public void setSink(String mask) {
		qd.setSink(getSubMask(mask));
	}
	private BoolArray2d getSubMask(String desired_colour) {
		//get boolean mask for mask colour best matching desired one
		Color wanted = Color.decode(desired_colour);
		double best_dist = Double.MAX_VALUE;
		int chosen_colour = -1;
		for (Integer present_colour: colourset)
		{
			Color c = new Color(present_colour);
			double dist = Math.pow(c.getRed() - wanted.getRed(),2) 
					+ Math.pow(c.getGreen() - wanted.getGreen(),2) 
					+ Math.pow(c.getBlue() - wanted.getBlue(),2);
			if (dist<best_dist)
			{
				best_dist = dist;
				chosen_colour = present_colour & 0xffffff;
			}
		}
		assert(chosen_colour!=-1);
		BoolArray2d submask = new BoolArray2d((int)(mask.getWidth()/scale),(int)(mask.getHeight()/scale));
		for (int x=0;x<qd.width;x++)
			for (int y=0;y<qd.height;y++)
				if ((mask.getRGB((int)(x*scale), (int)(y*scale))&0xffffff) == chosen_colour)
					submask.set(x,y,true);
		return submask;
	}
	private Dimension getCOG(BoolArray2d mask)
	{
		int xtot=0, ytot=0, n=0;
		for (int x=0;x<mask.width();x++)
			for (int y=0;y<mask.height();y++)
				if (mask.get(x,y))
				{
					xtot += x;
					ytot += y;
					n++;
				}
		return new Dimension(xtot/n,ytot/n);
	}
	public QuantumData getQD() {
		return qd;
	}
	public ControlState getControlState() {
		return controlstate;
	}
	public void updateGraphics() {
		if (controlstate.resetRequested())
			resetInitialState();
		if (!goalIsSounding())
			checkCounters();
		gr.update();
		lc.repaint();
	}
	private void checkCounters() {
		qd.getCounterScores(counters);
		allGoalsSatisfiedThisRound = true;
		for (QCounter c : counters)
			c.check();
		allGoalsSatisfiedThreadSafe |= allGoalsSatisfiedThisRound;
	}
	void resetInitialState()
	{
		qd.resetInitialState();
		for (QCounter c : counters)
			c.reset();
	}
	public GameRender getGameRender() {
		return gr;
	}
	public LevelComponent getLevelComponent() {
		return lc;
	}
	public BufferedImage getBackground() {
		return background;
	}
	public ArrayList<QCounter> getCounters() {
		return counters;
	}
	public void addGoal(String mask,int target)
	{
		BoolArray2d submask = getSubMask(mask);
		qd.addCounter(submask);
		QCounter gc;
		if (target > 0)
			gc = new PosGoalCounter(this,target,getCOG(submask));
		else
			gc = new NegGoalCounter(this,Math.abs(target),getCOG(submask));
		counters.add(gc);
	}
	public void addCollapse(String mask, int target, float sigma) {
		BoolArray2d submask = getSubMask(mask);
		qd.addCounter(submask);
		CollapseCounter cc = new CollapseCounter(this,target,getCOG(submask),sigma);
		counters.add(cc);
	}
	public void addTrap(String mask, int target) {
		BoolArray2d submask = getSubMask(mask);
		qd.addCounter(submask);
		TrapCounter tc = new TrapCounter(this,target,getCOG(submask));
		counters.add(tc);
	}
	public void addSteepeningValley() {
		final float topgrad = 1;
		final float botgrad = 3;
		final float maxpot = Math.max(topgrad,botgrad);
		for (int y=0;y<qd.height;y++)
		{
			final float grad = topgrad + (botgrad-topgrad)*(float)y/qd.height;
			for (int x=0;x<qd.width;x++)
			{
				final float dx = Math.abs(x-qd.width/2)/(float)(qd.width/2);
				qd.setPotential(x, y, dx*grad-maxpot);
			}
		}
	}
	public void clearWaveFunction() {
		qd.clearWaveFunction();
	}
	public void reportUnsatisfiedGoal() {
		allGoalsSatisfiedThisRound = false;
	}
	public boolean isSplash()
	{
		return isSplash;
	}
	public boolean sceneComplete() {
		if (isSplash())
			return controlstate.nextSplashRequested();
		else
			return allGoalsSatisfiedThreadSafe;
	}
	public boolean menuRequested()
	{
		return controlstate.menuRequested();
	}
	public boolean shouldTerminate()
	{
		return (!isReward && (sceneComplete() || controlstate.menuRequested())); 
	}
	public boolean goalSoundedIfNecessary()
	{
		return goalsoundedifnecessary;
	}
	public boolean goalIsSounding()
	{
		return goalSoundingClip != null;
	}
	public void soundCollapse() {
		playSound("sounds/collapse.wav",-10,false);
	}
	public void soundTrap() {
		playSound("sounds/trap.wav",-13,false);
	}
	void soundGoalIfNecessaryAsync() {
		if (!goalIsSounding())
		{
			if (!isSplash() && !controlstate.menuRequested())
				goalSoundingClip = playSound("sounds/goal.wav",-16,true);
			else
				goalsoundedifnecessary = true;
		}
	}
	void notifyGoalSounded()
	{
		goalsoundedifnecessary = true;
	}
	Clip playSound(String path,double gain,boolean terminateAfter)
	{
		InputStream input = new BufferedInputStream(getClass().getResourceAsStream(path));
		Clip clip = null;
		try {
			AudioInputStream audioIn = AudioSystem.getAudioInputStream(input);
			clip = AudioSystem.getClip();
			clip.open(audioIn);
			FloatControl v = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
			v.setValue((float)gain);
			clip.start();
			if (terminateAfter)
				clip.addLineListener(new LineListener(){
					public void update(LineEvent e)
					{
						if (e.getType()==LineEvent.Type.STOP)
							LevelManager.this.notifyGoalSounded();
					}
				});
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (UnsupportedAudioFileException e) {
			e.printStackTrace();
		}
		return clip;
	}
	public String getText() {
		return text;
	}
	public void addText(String textContent) {
		text = textContent;
	}
	public void setSynth(float pitchdown) {
		soundtrack = new Sonification(qd,pitchdown);
	}
	public void setLoop(String path)
	{
		soundtrack = new AudioLoop(path);
	}
	
	public void startSoundtrack() {
		soundtrack.start();
	}
	public void stopSoundtrack() {
		soundtrack.stop();
	}
	public void setLoopStart(double start) {
		((AudioLoop)soundtrack).setLoopStart(start);
	}
	public void setLoopLength(double length) {
		((AudioLoop)soundtrack).setLoopLength(length);
		
	}
	public long quantumFrameTimeNanos() {
		return quantumframetimenanos;
	}
}

class ControlState implements KeyListener, ActionListener
{
	float xslope,yslope;
	boolean cursorsdisabled;
	final float speed = 0.08f;
	public ControlState()
	{
		xslope = 0;
		yslope = 0;
		cursorsdisabled = false;
	}
	public void disableCursors()
	{
		cursorsdisabled = true;
	}
	public boolean resetRequested() {
		//only called once so can return current value and reset
		if (reset)
		{
			reset = false;
			return true;
		}
		else
			return false;
	}
	public boolean menuRequested() {
		//called multiple times 
		//but once menu is requested during class lifespan
		//it can't be cancelled, so no need to reset
		return menu;
	}
	public boolean nextSplashRequested() {
	//once next is requested during class lifespan
	//it can't be cancelled, so no need to reset
		return nextsplash;
	}
	private boolean up,down,left,right,reset,menu,nextsplash = false;
	private void queueReset()
	{
		reset = true;
	}
	private void queueMenu()
	{
		menu = true;
	}
	private void queueNextSplash()
	{
		nextsplash = true;
	}
	@Override
	public void keyPressed(KeyEvent e) {
		set(e.getKeyCode(),true);
	}
	@Override
	public void keyReleased(KeyEvent e) {
		set(e.getKeyCode(),false);	
	}
	@Override
	public void keyTyped(KeyEvent arg0) {}
	public void set(int key,boolean value)
	{
		switch (key)
		{
		case KeyEvent.VK_RIGHT:
			right = value;
			break;
		case KeyEvent.VK_LEFT:
			left = value;
			break;
		case KeyEvent.VK_UP:
			up = value;
			break;
		case KeyEvent.VK_DOWN:
			down = value;
			break;
		case KeyEvent.VK_R:
			queueReset();
			break;
		case KeyEvent.VK_M:
			queueMenu();
			break;
		case KeyEvent.VK_SPACE:
			if (!value)	queueNextSplash(); //only next splash on key release to prevent multiple skips
			break;
		default:
		}
	}	
	private byte getTargetXSlope() {
		if (left ^ right)
		{
			return (byte) (left?-1:1);
		}
		else
			return 0;
	}
	private byte getTargetYSlope() {
		if (up ^ down)
			return (byte) (up?-1:1);
		else
			return 0;
	}
	public float getXSlope(){
		if (!cursorsdisabled)
			return xslope;
		else
			return 0;
	}
	public float getYSlope()
	{
		if (!cursorsdisabled)
			return yslope;
		else
			return 0;
	}
	private float getNewSlope(float oldslope,float target)
	{
		if (Math.abs(oldslope-target)<speed) 
			return target;
		else
		{
			final float movedir = Math.signum(target-oldslope);
			float newslope = oldslope+speed*movedir;
			if (newslope<-1) newslope=-1;
			else if (newslope>1) newslope=1;
			return newslope;
		}
	}
	public void step()
	{
		xslope = getNewSlope(xslope,getTargetXSlope());
		yslope = getNewSlope(yslope,getTargetYSlope());
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand()=="RESET")
			queueReset();
		else if (e.getActionCommand()=="MENU")
			queueMenu();
		else if (e.getActionCommand()=="NEXTSPLASH")
			queueNextSplash();
	}
}

