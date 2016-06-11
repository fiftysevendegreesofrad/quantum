package quantum;

import java.awt.Color;
import java.awt.Dimension;

interface QCounter {
	Color getColour();
	String getText();
	void setValue(int v);
	public Dimension getTextLocation();
	public void check();
	public void reset();
}

abstract class BaseCounter
{
	int target;
	int value;
	Dimension textlocation;
	LevelManager lm;
	public void setValue(int v) {value = v;}
	public Dimension getTextLocation() {return textlocation;}
	public void reset() {value = 0;}
}

class PosGoalCounter extends BaseCounter implements QCounter {
	public PosGoalCounter(LevelManager lm,int target,Dimension textlocation) { 
		this.target = target; 
		this.textlocation = textlocation;
		this.lm = lm;
	}
	public Color getColour() { return Color.WHITE; }
	public String getText() { return String.format("GOAL+\n%d/%d%%", value, target); }
	public void check() { if (value<target) lm.reportUnsatisfiedGoal(); }
}

class NegGoalCounter extends BaseCounter implements QCounter {
	public NegGoalCounter(LevelManager lm,int target,Dimension textlocation) { 
		this.target = target; 
		this.textlocation = textlocation;
		this.lm = lm;
	}
	public Color getColour() { return Color.BLUE; }
	public String getText() { return String.format("ANTIGOAL\n%d/%d%%", value, target); }
	public void check() { if (value>target) lm.reportUnsatisfiedGoal(); }
}

class CollapseCounter extends BaseCounter implements QCounter {
	float sigma;
	boolean active = true;
	public CollapseCounter(LevelManager lm,int target,Dimension textlocation,float sigma) {
		this.target = target; 
		this.textlocation = textlocation;
		this.sigma = sigma;
		this.lm = lm;
	}
	public Color getColour() { return Color.YELLOW; }
	public String getText() { 
		if (active)
			return String.format("COLLAPSE\n%d/%d%%", value, target); 
		else
			return "";
	}
	public void check()
	{
		if (active && value >= target)
		{
			lm.clearWaveFunction();
			lm.addGaussianQUnits((int)textlocation.getWidth(), (int)textlocation.getHeight(), sigma,0,0,1);
			active = false;
			lm.soundCollapse();
		}
	}
	public void reset() { active=true; super.reset();}
}

class TrapCounter extends BaseCounter implements QCounter {
	public TrapCounter(LevelManager lm,int target,Dimension textlocation) {
		this.target = target; 
		this.textlocation = textlocation;
		this.lm = lm;
	}
	public Color getColour() { return Color.RED; }
	public String getText() { 
		return String.format("TRAP\n%d/%d%%", value, target); 
	}
	public void check()
	{
		if (value >= target)
		{
			lm.resetInitialState();
			lm.soundTrap();
		}
	}
}
