package quantum;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.PriorityQueue;

import javax.imageio.ImageIO;

//ideas for speeding up:
//1. making it a final class and the members private final (done - no different)
//2. re expressing everything as primitives.  tried; factoring out complex class made only small difference
//3. new array2d class interleaving real and imag?
//4. multithreading... would reduce portability
//5. integer arithmetic.  tried, only saved 26% of physics cpu time (20% of total) and couldn't get it to converge
//6. writing to make use of SIMD alas doesn't really work on java, I tried
//7. manual inlining and getting loops right way round, that helped a lot
//8. replacing condtional on walls with intwalls multiplication.  doesn't seem to help but i should 
//test a case with lots of walls that might screw up branch prediction.  ok i have.  have now removed intwalls.
final class Complex{
	private final float real,imag;
	public Complex(float r,float i) {real=r;imag=i;}
	public Complex() {real=0;imag=0;}
	public Complex add(Complex c) {return new Complex(real+c.real,imag+c.imag);}
	public static Complex negate(Complex c) {return new Complex(-c.real,-c.imag);}
	public Complex sub(Complex c) {return add(negate(c));}
	public Complex divi() {return new Complex(imag,-real);}
	public Complex mult(float f) {return new Complex(imag*f,real*f);}
	public float mod2() {return real*real+imag*imag;} 
	public void print() { System.out.println(real+" "+imag);}
	public float real() {return real;}
	public float imag() {return imag;}
}

final class FloatArray2d 
{
	private float[] data;
	private int width;
	public FloatArray2d(int width,int height)
	{
		data = new float[width*height];
		this.width = width;
	}
	public void setEqualTo(FloatArray2d other)
	{
		width = other.width;
		data = other.data.clone();
	}
	public float get(int x,int y) {return data[x+width*y];}
	public void set(int x,int y,float v) {data[x+width*y]=v;}
	public float del2(int x,int y)
	{
		return get(x,y-1)+get(x,y+1)+get(x-1,y)+get(x+1,y)-4*get(x,y);
	}
}
final class ByteArray2d
{
	private final byte[] data;
	private int width;
	public ByteArray2d(int width,int height)
	{
		data = new byte[width*height];
		this.width = width;
	}
	public byte get(int x,int y) {return data[x+width*y];}
	public void set(int x,int y,byte v) {data[x+width*y]=v;}
}
final class BoolArray2d
{
	final private boolean[] data;
	private int width, height;
	public BoolArray2d(int width,int height)
	{
		data = new boolean[width*height];
		this.width = width;
		this.height = height;
	}
	public int width() {return width;}
	public int height() {return height;}
	public boolean get(int x,int y) {return data[x+width*y];}
	public void set(int x,int y,boolean v) {data[x+width*y]=v;}
}

final class QuantumData {
	final private FloatArray2d levelDesignPotential,pot_cache;
	final FloatArray2d sink_mult;
	final private FloatArray2d real,imag,init_real,init_imag;
	private BoolArray2d walls, sink;
	private ArrayList<BoolArray2d> counters;
	private float delta_t,maxtilt;
	boolean running = false;
	
	final public int width,height;
	private ControlState controlstate;
	
	public QuantumData(int width,int height,ControlState ks) {
		controlstate = ks;
		this.width = width;
		this.height = height;
		real = new FloatArray2d(width,height);
		imag = new FloatArray2d(width,height);
		init_real = new FloatArray2d(width,height);
		init_imag = new FloatArray2d(width,height);
		walls = new BoolArray2d(width,height);
		sink = new BoolArray2d(width,height);
		sink_mult = new FloatArray2d(width,height);
		levelDesignPotential = new FloatArray2d(width,height);
		pot_cache = new FloatArray2d(width,height);
		counters = new ArrayList<BoolArray2d>();
	}
	private void saveInitialState()
	{
		init_real.setEqualTo(real);
		init_imag.setEqualTo(imag);
	}
	public void resetInitialState()
	{
		real.setEqualTo(init_real);
		imag.setEqualTo(init_imag);
	}
	public void clearWaveFunction()
	{
		for (int y=0;y<height;y++)
			for (int x=0;x<width;x++)
			{
				real.set(x,y,0);
				imag.set(x,y,0);
			}
	}
	public void setDeltaT(float dt) {delta_t = dt;}
	public void setMaxTilt(float mt) {maxtilt = mt;}
	public void addGaussian(int xc,int yc,float sigma,float fx,float fy,float ascale)
	{
		double a = ascale * Math.pow(2*Math.PI*sigma*sigma,-0.25);
		float d = 4*sigma*sigma;
		float omegax = (float) (2.*Math.PI*fx); //fixme this seems wrong
		float omegay = (float) (2.*Math.PI*fy); //fixme this seems wrong
		for (int x=1;x<width-1;x++)
			for (int y=1;y<height-1;y++)
			{
				double r2 = (x-xc)*(x-xc)+(y-yc)*(y-yc);
				float vr = (float) (a * Math.exp(-r2/d)
						* Math.cos(omegax*(float)x/width)
						* Math.cos(omegay*(float)y/height));
				float vi = (float) (a * Math.exp(-r2/d)
						* Math.sin(omegax*(float)x/width)
						* Math.sin(omegay*(float)y/height));
				real.set(x,y,real.get(x,y)+vr);
				imag.set(x,y,imag.get(x,y)+vi);
			}
	}
	public void addDelta(int xc,int yc,float a)
	{
		assert(!running);
		real.set(xc,yc,a);
		imag.set(xc,yc,a);
	}
	public Complex get(int x,int y) {return new Complex(real.get(x,y),imag.get(x,y));}
	private void reset_potential_cache()
	{
		//potentials >0 are problematic
		//pixel wide band with potential +1 above background - tunnelling
		//potential of -5 over width of universe - good for steering
		
		//if tilting 2 directions at once reduce tilt to compensate
		final float totalslope = Math.abs(controlstate.getXSlope())+Math.abs(controlstate.getYSlope());
		final float tilt = (totalslope<=1) ? maxtilt : maxtilt/totalslope;
		
		//compute desired relative potentials of corners
		final float biggerdim = Math.max(width, height);
		final float right_change = -controlstate.getXSlope()*tilt*width/biggerdim;
		final float bottom_change = -controlstate.getYSlope()*tilt*height/biggerdim;
		final float topleft = -right_change - bottom_change;
		final float topright = right_change - bottom_change;
		final float bottomleft = -right_change + bottom_change;
		final float bottomright = right_change + bottom_change;
		
		//adjust all potentials to be <0
		final float max = Math.max(Math.max(Math.max(topleft,topright), bottomleft), bottomright);
		final float newtopleft = topleft - max;
		
		//compute per-simulation-element steps in potential to efficiently compute
		final float x_pot_step = right_change/width;
		final float y_pot_step = bottom_change/width;
		float left_edge_pot = newtopleft;
		for (int y=1;y<height-1;y++)
		{
			left_edge_pot += y_pot_step;
			float current_pot = left_edge_pot;
			for (int x=1;x<width-1;x++)
			{
				current_pot += x_pot_step;
				pot_cache.set(x,y,current_pot + levelDesignPotential.get(x, y));
			}
		}
	}
	private void ensure_no_positive_potential()
	{
		float maxpot = Float.NEGATIVE_INFINITY;
		for (int x=0;x<width;x++)
			for (int y=0;y<height;y++)
			{
				final float pot = levelDesignPotential.get(x, y);
				if (pot>maxpot) maxpot=pot;
			}
		for (int x=0;x<width;x++)
			for (int y=0;y<height;y++)
				levelDesignPotential.set(x, y,
						levelDesignPotential.get(x,y)-maxpot);
	}
	private void add_walls()
	{
		for (int x=1;x<width-1;x++)
			for (int y=1;y<height-1;y++)
			{
				if (walls.get(x,y)) {
					real.set(x,y,0);
					imag.set(x,y,0);
				}
			}
	}
	public void step()
	{
		if (!running)
		{
			running = true;
			setupSinkMult();
			add_walls(); //must be done before saveInitialState
			saveInitialState(); 
			ensure_no_positive_potential();
		}
		controlstate.step();
		reset_potential_cache();
		//boundaries are never computed, hence left at 0
		for (int y=1;y<height-1;y++)
			for (int x=1;x<width-1;x++)
			{
				if (!walls.get(x,y))
					real.set(x,y, 
							sink_mult.get(x,y)*
						(real.get(x,y) + delta_t * (-0.5f * 
							(imag.get(x,y-1)+imag.get(x,y+1)+imag.get(x-1,y)+imag.get(x+1,y)-4*imag.get(x,y))
							+ pot_cache.get(x,y)*imag.get(x,y))));
			}
		//I have inlined del2, it does make it faster
		//Inlining could happen automatically with vm options -XX:FreqInlineSize=50 -XX:MaxInlineSize=50
		//But these are not universally supported or guaranteed not to change in future
		for (int y=1;y<height-1;y++)
			for (int x=1;x<width-1;x++)
			{
				if (!walls.get(x,y))
					imag.set(x,y,
							sink_mult.get(x,y)*
						(imag.get(x,y) - delta_t * (-0.5f *  
							(real.get(x,y-1)+real.get(x,y+1)+real.get(x-1,y)+real.get(x+1,y)-4*real.get(x,y))
							+ pot_cache.get(x,y)*real.get(x,y))));
			}
	}
	private void setupSinkMult() {
		//flood fill sink_mult with 0 where not a sink; otherwise distance in pixels from non-sink
		//...basically a mini-Dijkstra
		final class Pixel implements Comparable<Pixel>{ 
			public int x,y,d; 
			public Pixel(int xx,int yy,int dd) {x=xx;y=yy;d=dd;}
			public int compareTo(Pixel other) { return Integer.compare(d,other.d);}
		}
		PriorityQueue<Pixel> queue = new PriorityQueue<Pixel>();
		for (int y=0;y<height;y++)
			for (int x=0;x<width;x++)
			{
				sink_mult.set(x,y,Float.POSITIVE_INFINITY);
				if (!sink.get(x,y) && !walls.get(x,y))
					queue.add(new Pixel(x,y,0));
			}
		while (!queue.isEmpty())
		{
			Pixel p = queue.poll();
			if (sink_mult.get(p.x,p.y) > p.d)
			{
				sink_mult.set(p.x,p.y,p.d);
				for (int dx=-1;dx<=1;dx+=2)
					for (int dy=-1;dy<=1;dy+=2)
					{
						Pixel q = new Pixel(p.x+dx,p.y+dy,p.d+1);
						if (q.x>=0 && q.x<width && q.y>=0 && q.y<height)
							queue.add(q);
					}
			}
		}
		//now convert these to actual sink_mults
		final float suddenness = 0.005f;
		for (int y=0;y<height;y++)
			for (int x=0;x<width;x++)
			{
				final float dist = sink_mult.get(x,y);
				sink_mult.set(x,y,(float) Math.exp(-Math.pow(dist/2,2)*suddenness));
			}
	}
	public void setPotential(int x, int y, float p) {
		levelDesignPotential.set(x, y, p);
	}
	public void addPotential(int x,int y, float p) {
		levelDesignPotential.set(x, y, p + levelDesignPotential.get(x,y));
	}
	public void setWalls(BoolArray2d subMask) {
		assert(subMask.width() == width);
		assert(subMask.height() == height);
		walls = subMask;
	}
	public void setSink(BoolArray2d subMask) {
		assert(subMask.width() == width);
		assert(subMask.height() == height);
		sink = subMask;
	}
	public void addCounter(BoolArray2d subMask) {
		counters.add(subMask);
	}
	public void getCounterScores(ArrayList<QCounter> counters2)
	{
		assert(counters.size()==counters2.size());
		
		//find total probability for renormalization
		double totalprob = 0;
		for (int x=0;x<width;x++)
			for (int y=0;y<height;y++)
				totalprob += get(x,y).mod2();
		
		for (int counterid=0;counterid<counters.size();counterid++)
		{
			double score = 0;
			BoolArray2d currentcounter = counters.get(counterid);
			for (int x=0;x<width;x++)
				for (int y=0;y<height;y++)
					if (currentcounter.get(x,y))
						score += get(x,y).mod2();
			counters2.get(counterid).setValue((int)Math.floor((float)(score/totalprob*100)));
		}
	}
	public float getPotential(int x, int y) {
		return levelDesignPotential.get(x,y);
	}
}


interface ColourMap
{
	public int process(Complex c);
	public void resetGain();
}

class AmpColourMap implements ColourMap
{
	private final float gamma = 0.7f;
	private final int maxindex = 255;
	private final int[] lookup;
	private float max = 0;
	private float gain = 0;
	public AmpColourMap()
	{
		lookup = new int[maxindex+1];
		for (int i=0;i<maxindex+1;i++)
			lookup[i] = (int) (255*Math.pow((float)i/maxindex,gamma));
	}
	public int process(Complex c)
	{
		final float source = c.mod2(); 
		if (source>max) max=source;
		int index = (int) (source*gain);
		if (index>maxindex) index=maxindex;
		final int alpha = lookup[index];
		final int red = 255;
		final int green = 255;
		final int blue = 255;
		return (alpha<<24)|(red<<16)|(green<<8)|blue;
	}
	public void resetGain()
	{
		gain = maxindex/max;
		max = 0;
	}
}

class RainbowColourMap implements ColourMap
{
	private float max = 0;
	private float gain = 0;
	private final int[] colourindex;
	private static final float max_brightness = 1.f;
	private static final float saturation = 1.f;
	private static final float gamma = 1.f;
	//bodge of a perceptual colour space by turning down the red+green a bit:
	private static final float red_compensate = 0.9f;
	private static final float green_compensate = red_compensate*0.8f/0.9f;
	private static final int colourspace_size = 512;
	public RainbowColourMap()
	{
		colourindex = new int[colourspace_size*colourspace_size];
		for (int x=0;x<colourspace_size;x++)
			for (int y=0;y<colourspace_size;y++)
			{
				int dx = x-colourspace_size/2;
				int dy = y-colourspace_size/2;
				float mag = (float)Math.pow(dx*dx+dy*dy, 0.5);
				if (mag>colourspace_size/2) mag=colourspace_size/2;
				int alpha = (int) (Math.pow(mag/(colourspace_size/2),gamma)*255);
				float phase = (float)(Math.atan2(dy,dx)/2/Math.PI);
				final Color c1 = new Color(Color.HSBtoRGB(phase, saturation, max_brightness));
				final Color c2 = new Color((int)(c1.getRed()*red_compensate),
						(int)(c1.getGreen()*green_compensate),c1.getBlue());
				int c = c2.getRGB();
				c &= 0xffffff;
				c |= alpha << 24;
				colourindex[x+colourspace_size*y] = c;
			}
	}
	public int process(Complex c)
	{
		float r = c.real();
		float i = c.imag();
		float mod2 = c.mod2();
		if (mod2>max) max=mod2;
		int r_index = (int) (r*gain+colourspace_size/2);
		int i_index = (int) (i*gain+colourspace_size/2);
		if (r_index<0) r_index=0;
		if (i_index<0) i_index=0;
		if (r_index>colourspace_size-1) r_index=colourspace_size-1;
		if (i_index>colourspace_size-1) i_index=colourspace_size-1;
		return colourindex[r_index+colourspace_size*i_index];
	}
	public void resetGain()
	{
		gain = (float) Math.pow(max,-0.5)*colourspace_size/2;
		max = 0;
	}
}

class GameRender {
	public int[] data;
	QuantumData qd;
	ColourMap colourmap;
	private BufferedImage image;
	public int width() {return (int) (qd.width);}
	public int height() {return (int) (qd.height);}
	public void setAmpColourMap() { colourmap = new AmpColourMap();}
	public void setRainbowColourMap() { colourmap = new RainbowColourMap();}
	public GameRender(QuantumData source)
	{
		this.qd = source;
		image = new BufferedImage(qd.width, qd.height, BufferedImage.TYPE_INT_ARGB);
		data = new int[width()*height()];
		colourmap = new AmpColourMap();
	}
	public void update()
	{
		final boolean showpotential = false; //for debugging
		//it may seem perverse to calculate 'data' only to copy it into 'image'
		//rather than just calculate image.  but i profiled and it's faster.
		for (int y=0;y<qd.height;y++)
			for (int x=0;x<qd.width;x++)
			{
				final Complex point = showpotential ? new Complex(0,qd.getPotential(x, y)) : qd.get(x,y);
				data[x+qd.width*y] = colourmap.process(point); 
			}
		colourmap.resetGain();
		image.setRGB(0, 0, width(), height(), data, 0, width());
	}
	public Image getImage() {
		return image;
	}
}

//This was used to generate some level backdrops
class PotentialGridGenerator
{
	final static int gridcolour = 0xffcccccc;
	final static int bgcolour = 0xff000000;
	final static int gridsquaresize = 40;
	final static float basedistance = 1;
	
	public static void drawPotential(QuantumData qd,String filename,String ext)
	{
		int width = qd.width;
		int height = qd.height;
		int[] data = new int[width*height];
		final int cx = width/2;
		final int cy = height/2;
		drawPotentialPartial(qd,data,cx,cy,-1,-1);
		drawPotentialPartial(qd,data,cx,cy,-1,height);
		drawPotentialPartial(qd,data,cx,cy,width,-1);
		drawPotentialPartial(qd,data,cx,cy,width,height);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, height, data, 0, width);
		try {
			ImageIO.write(image, ext, new File(filename+"."+ext));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static void drawPotentialPartial(QuantumData qd,int[] data,int xstart,int ystart,int xend,int yend)
	{
		//draw grid representing potential
		float[] lastgridsquarey = new float[qd.width];
		for (int i=0;i<lastgridsquarey.length;i++)
			lastgridsquarey[i]=-gridsquaresize;
		float[] gridposy = new float[qd.width];
		int xstep = (int) Math.signum(xend-xstart);
		int ystep = (int) Math.signum(yend-ystart);
		for (int y=ystart+ystep;y!=yend;y+=ystep)
		{
			float lastgridsquarex = -gridsquaresize;
			float gridposx = 0;
			for (int x=xstart+xstep;x!=xend;x+=xstep)
			{
				final float d = -qd.getPotential(x, y)+basedistance;
				gridposx += d;
				gridposy[x] += d;
				boolean drawgridpixel = false;
				if (gridposx-lastgridsquarex>=gridsquaresize)
				{
					drawgridpixel = true;
					lastgridsquarex = gridposx;
				}
				if (gridposy[x]-lastgridsquarey[x]>=gridsquaresize)
				{
					drawgridpixel = true;
					lastgridsquarey[x] = gridposy[x];
				}
				data[x+qd.width*y] = drawgridpixel?gridcolour:bgcolour;
			}
		}
	}
}
