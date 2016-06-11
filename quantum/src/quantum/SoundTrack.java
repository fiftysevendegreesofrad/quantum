package quantum;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

interface Soundtrack {
	public void start();
	public void stop();
}

class Silence implements Soundtrack {
	public void start() {}
	public void stop() {}
}

class SonificationSynthSynchronous {
	//synth params
	float pitchdown;
	static final float audioGrainExtraFadeProportion = 0.9f;
	static final int line_end_fade = 1000; 
	static final int grains_per_line = 4; //this affects output pitch more than I would like, but that's it's nature
	private final int lines_per_frame = 16; //artistic decision
	
	//data structures
	QuantumData qd;
	SourceDataLine soundout;
	byte[] audio_out_bytes;
	float[] frame_samples_left, frame_samples_right;
	int line_samples_length;
	float samplerate;
	float[] grainFadeSigmoid, lineFadeSigmoid;
	private int qd_ystep, audioGrainInnerLength, audioGrainBonusSamplesFadeEachEnd, audioGrainOuterHalfLength, qd_x_grainstep;
	
	SonificationSynthSynchronous(QuantumData qd,float pitchdown)
	{
		this.pitchdown = pitchdown;
		this.qd = qd;
		samplerate = 44100/pitchdown;
		try {
			soundout = AudioSystem.getSourceDataLine(new AudioFormat(samplerate, 16, 2, true, false));
			soundout.open();
			soundout.start();
		} catch (LineUnavailableException e) {
			soundout = null;
			e.printStackTrace();
		}
		final float desired_line_time = 2.f/lines_per_frame;
		line_samples_length = (int) (desired_line_time*samplerate);
		frame_samples_left = new float[line_samples_length*lines_per_frame];
		frame_samples_right = new float[line_samples_length*lines_per_frame];
		audio_out_bytes = new byte[line_samples_length*lines_per_frame*2*2];
		qd_ystep = qd.height/16; //artistic choice
		//each grain goes [samplesfade][innerlength][samplesfade]
		audioGrainInnerLength = line_samples_length / grains_per_line;
		audioGrainBonusSamplesFadeEachEnd = (int) (audioGrainInnerLength*audioGrainExtraFadeProportion/2); // /2 because each end
		audioGrainOuterHalfLength = audioGrainInnerLength/2 + audioGrainBonusSamplesFadeEachEnd;
		qd_x_grainstep = qd.width/grains_per_line; 
		grainFadeSigmoid = new float[audioGrainBonusSamplesFadeEachEnd+1];
		makeSigmoid(grainFadeSigmoid);
		lineFadeSigmoid = new float[line_end_fade+1];
		makeSigmoid(lineFadeSigmoid);
	}
	private void makeSigmoid(float[] array)
	{
		final int length = array.length;
		for (int i=0;i<length;i++)
			array[i] = (float) (1-(Math.cos((float)i/length*Math.PI)+1)/2);
	}
	public void updateAudio() {
		for (int x=0;x<line_samples_length*lines_per_frame;x++)
		{
			frame_samples_left[x]=0;
			frame_samples_right[x]=0;
		}
		float audiomax = 0;
		for (int line=0;line<lines_per_frame;line++)
		{
			final int y = line*qd_ystep + qd_ystep/2;
			final int right_chan_q_ypos = y>1?y-1:y+1;
			for (int g=0;g<grains_per_line;g++)
			{
				//graincentre - evenly spaced over line width
				int quantumGrainCentre = g*qd_x_grainstep + qd_x_grainstep/2;
				int audioGrainCentre = g*audioGrainInnerLength + audioGrainInnerLength/2;
				int lineoutputsamplepos = audioGrainCentre-audioGrainOuterHalfLength - 1;
				for (int qx = (int) (quantumGrainCentre-audioGrainOuterHalfLength);
						qx<quantumGrainCentre+audioGrainOuterHalfLength;
						qx++)
				{
					lineoutputsamplepos++;
					//we could be asking for qd outside of domain (if qd is shorter than linelength+grainfades)
					//or audio data outside of domain (for fades on first/last samples of line)
					if (lineoutputsamplepos<0 || lineoutputsamplepos>=line_samples_length
							|| qx<0 || qx>=qd.width)
						continue;
					
					float grainvol;
					final int audioGrainDistFromCentre = Math.abs(qx-quantumGrainCentre); 
					if (audioGrainDistFromCentre<audioGrainInnerLength/2)
						grainvol = 1.f;
					else
					{
						final int excess = audioGrainDistFromCentre-audioGrainInnerLength/2;
						grainvol = grainFadeSigmoid[audioGrainBonusSamplesFadeEachEnd-excess];
					}
					float linefade = 1.f;
					int distFromEnd = Math.min(lineoutputsamplepos,line_samples_length-1-lineoutputsamplepos);
					if (distFromEnd < line_end_fade)
						linefade = lineFadeSigmoid[distFromEnd];

					final int frameoutputsamplepos = lineoutputsamplepos + line*line_samples_length;
					final float phi_l = qd.get(qx,y).mod2()+qd.get(qx,y).real()/10;
					frame_samples_left[frameoutputsamplepos] += phi_l*grainvol*linefade;
					final float phi_r = qd.get(qx,right_chan_q_ypos).mod2()-qd.get(qx,y).real()/10; 
					frame_samples_right[frameoutputsamplepos] += phi_r*grainvol*linefade;
					if (Math.abs(frame_samples_left[frameoutputsamplepos])>audiomax) audiomax=frame_samples_left[frameoutputsamplepos];
					if (Math.abs(frame_samples_right[frameoutputsamplepos])>audiomax) audiomax=frame_samples_right[frameoutputsamplepos];
				}
			}
		}
		final float gain = 32767.f / audiomax;
		int bufferpos = 0;
		for (int i=0;i<line_samples_length*lines_per_frame;i++)
		{
			final short sampleval_l = (short) (gain*frame_samples_left[i]);
			audio_out_bytes[bufferpos++] = (byte) (sampleval_l&0xff);
			audio_out_bytes[bufferpos++] = (byte) (sampleval_l>>8);
			final short sampleval_r = (short) (gain*frame_samples_right[i]);
			audio_out_bytes[bufferpos++] = (byte) (sampleval_r&0xff);
			audio_out_bytes[bufferpos++] = (byte) (sampleval_r>>8);
		}
		assert(bufferpos == audio_out_bytes.length);
		soundout.write(audio_out_bytes,0,bufferpos);
		soundout.drain();
	}
}

class Sonification implements Soundtrack
{
	private SonificationSynthSynchronous synth;
	private boolean shouldTerminate;
	private Thread thread;
	public Sonification(QuantumData qd,float pitchdown)
	{
		synth = new SonificationSynthSynchronous(qd,pitchdown);
		shouldTerminate = false;
		thread = new Thread(new Runnable(){
			public void run()
			{
				while (!shouldTerminate)
					synth.updateAudio();
			}
		});
	}
	public void start()
	{
		thread.start();
	}
	public void stop()
	{
		shouldTerminate = true;
	}
}

class AudioLoop implements Soundtrack {
	MediaPlayer mediaPlayer;
	Duration startpos;
	public AudioLoop(String path)
	{
		System.out.println("Loading audio "+path);
		Media media = new Media(path);
		mediaPlayer = new MediaPlayer(media);
		mediaPlayer.setVolume(0.5);
		mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE); //repeat until stopped
	}
	public void start()
	{
		mediaPlayer.play();
	}
	public void stop()
	{
		mediaPlayer.stop();
	}
	public void setLoopStart(double start) {
		startpos = new Duration(start*1000);
		mediaPlayer.setStartTime(startpos);
	}
	public void setLoopLength(double length) {
		mediaPlayer.setStopTime(startpos.add(new Duration(length*1000)));
	}
}